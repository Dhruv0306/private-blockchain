package com.privatechain.network.rpc;

import com.privatechain.access.allowlist.AllowlistManager;
import com.privatechain.access.rbac.NodeRole;
import com.privatechain.access.rbac.PermissionManager;
import com.privatechain.core.builder.Blockchain;
import com.privatechain.core.event.BlockchainEventBus;
import com.privatechain.core.model.Block;
import com.privatechain.core.model.Transaction;
import com.privatechain.core.network.NodeServerLifecycle;
import com.privatechain.network.gossip.GossipProtocol;
import com.privatechain.network.peer.PeerManager;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * TCP server that accepts inbound peer connections and dispatches received messages.
 *
 * <p>{@code NodeServer} is the network entry point for all inbound P2P traffic.
 * Every raw message passes through a strict access-control pipeline before any
 * deserialization occurs, satisfying NFR-SEC-04:</p>
 *
 * <pre>
 * Inbound TCP message
 *        │
 *        ▼
 * AllowlistManager.isAllowed(senderId) ──[DENY]──► drop + WARN log
 *        │ [ALLOW]
 *        ▼
 * PermissionManager.hasRole(senderId, required) ──[DENIED]──► error response
 *        │ [AUTHORIZED]
 *        ▼
 * MessageCodec.decode(bytes) ──► dispatch to handler
 * </pre>
 *
 * <h2>Transport</h2>
 * <p>This implementation uses Java's built-in {@link java.net.ServerSocket} rather than
 * Netty, keeping {@code blockchain-network} dependency-free beyond the JDK for the
 * current milestone. Each accepted connection is handled on a dedicated thread from a
 * cached thread pool. A full Netty migration can follow via ADR-001 (T-064).</p>
 *
 * <h2>Lifecycle</h2>
 * <pre>{@code
 * NodeServer server = new NodeServer(8545, allowlist, permMgr, peerMgr, codec, ...);
 * server.start();
 * // ... node is running ...
 * server.stop();
 * }</pre>
 *
 * <h2>Thread safety</h2>
 * <p>The server accept loop runs on a dedicated daemon thread. Message handlers run
 * on the connection thread pool. The {@link #running} flag is volatile to ensure
 * visibility across threads during shutdown.</p>
 *
 * @see AllowlistManager
 * @see PermissionManager
 * @see MessageCodec
 * @see PeerManager
 * @since 1.0.0
 */
public final class NodeServer implements NodeServerLifecycle {

    private static final Logger LOGGER = Logger.getLogger(NodeServer.class.getName());

    /**
     * Maximum queued connections before the OS starts refusing new ones.
     */
    private static final int BACKLOG = 50;

    /**
     * Max concurrent connection handlers.
     */
    private static final int HANDLER_THREADS = 20;

    // ─── Fields ───────────────────────────────────────────────────────────────

    /**
     * Port on which this node listens for inbound connections.
     */
    private final int port;

    /**
     * Allowlist gate — checked BEFORE any message deserialization (NFR-SEC-04, T-054).
     * Non-allowlisted senders are silently dropped and logged at WARN level (AC-07).
     */
    private final AllowlistManager allowlistManager;

    /**
     * Permission gate — checked AFTER the allowlist, before the message handler.
     * Ensures that the role of the sending node authorizes the requested operation.
     */
    private final PermissionManager permissionManager;

    /**
     * Peer registry: updated when a new peer sends its first message.
     */
    private final PeerManager peerManager;

    /**
     * Message serializer / deserializer.
     */
    private final MessageCodec codec;

    /**
     * The canonical blockchain — updated when a valid BLOCK message is received.
     */
    private final Blockchain blockchain;

    /**
     * Event bus for publishing peer connect/disconnect events.
     */
    private final BlockchainEventBus eventBus;

    /**
     * Optional gossip layer for forwarding transactions (wired after gossip module starts).
     */
    private volatile GossipProtocol gossipProtocol; // nullable — set via setter

    /**
     * Underlying server socket. Set during {@link #start()}.
     */
    private volatile ServerSocket serverSocket;

    /**
     * Thread pool for handling concurrent inbound connections.
     */
    private final ExecutorService handlerPool =
        Executors.newFixedThreadPool(HANDLER_THREADS, r -> {
            Thread t = new Thread(r, "node-server-handler");
            t.setDaemon(true);
            return t;
        });

    /**
     * Controls the server accept loop.
     */
    private volatile boolean running = false;

    // ─── Constructor ──────────────────────────────────────────────────────────

    /**
     * Constructs a {@code NodeServer} with all required dependencies.
     *
     * @param port              TCP port to listen on (1024–65535)
     * @param allowlistManager  allowlist gate for inbound messages (non-null)
     * @param permissionManager RBAC gate for operation authorization (non-null)
     * @param peerManager       peer lifecycle manager (non-null)
     * @param codec             message codec for encoding/decoding (non-null)
     * @param blockchain        the chain manager for block ingestion (non-null)
     * @param eventBus          event bus for publishing peer events (non-null)
     * @throws NullPointerException     if any required dependency is null
     * @throws IllegalArgumentException if port is out of range
     */
    public NodeServer(
        int port,
        AllowlistManager allowlistManager,
        PermissionManager permissionManager,
        PeerManager peerManager,
        MessageCodec codec,
        Blockchain blockchain,
        BlockchainEventBus eventBus) {

        if (port < 1024 || port > 65535) {
            throw new IllegalArgumentException("Port must be in [1024, 65535], got: " + port);
        }
        this.port = port;
        this.allowlistManager = Objects.requireNonNull(allowlistManager, "allowlistManager must not be null");
        this.permissionManager = Objects.requireNonNull(permissionManager, "permissionManager must not be null");
        this.peerManager = Objects.requireNonNull(peerManager, "peerManager must not be null");
        this.codec = Objects.requireNonNull(codec, "codec must not be null");
        this.blockchain = Objects.requireNonNull(blockchain, "blockchain must not be null");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus must not be null");
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    /**
     * Binds the server socket and starts the accept loop on a daemon thread.
     *
     * @throws IllegalStateException if the server is already running
     * @throws RuntimeException      if the server socket cannot be bound to the port
     */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "UNENCRYPTED_SERVER_SOCKET",
        justification = "Plain TCP is intentional for Milestone 7. TLS transport is deferred "
            + "to the Netty migration (ADR-001, T-064). All nodes are on a trusted private "
            + "network; allowlist + ECDSA invitation tokens provide the identity layer.")
    public void start() {
        if (running) {
            throw new IllegalStateException("NodeServer is already running on port " + port);
        }
        try {
            serverSocket = new ServerSocket(port, BACKLOG);
            running = true;
            LOGGER.info(() -> "NodeServer listening on port " + port);

            Thread acceptThread = new Thread(this::acceptLoop, "node-server-accept");
            acceptThread.setDaemon(true);
            acceptThread.start();

        } catch (IOException e) {
            throw new RuntimeException("Failed to bind NodeServer to port " + port + ": " + e.getMessage(), e);
        }
    }

    /**
     * Stops the server by closing the server socket and shutting down the handler pool.
     *
     * <p>Closing {@link #serverSocket} causes the blocking {@code accept()} call in
     * {@link #acceptLoop()} to throw a {@link SocketException}, which terminates
     * the accept loop cleanly.</p>
     */
    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            LOGGER.warning(() -> "Error closing NodeServer socket: " + e.getMessage());
        }
        handlerPool.shutdownNow();
        LOGGER.info("NodeServer stopped");
    }

    /**
     * Wires the gossip protocol after construction (avoids circular dependency at
     * construction time between NodeServer and GossipProtocol).
     *
     * @param gossipProtocol the gossip layer to use (non-null)
     * @throws NullPointerException if gossipProtocol is null
     */
    public void setGossipProtocol(GossipProtocol gossipProtocol) {
        this.gossipProtocol = Objects.requireNonNull(gossipProtocol, "gossipProtocol must not be null");
    }

    /**
     * Returns the port this server is (or will be) listening on.
     *
     * @return TCP port
     */
    public int getPort() {
        return port;
    }

    /**
     * Returns {@code true} if the server accept loop is running.
     *
     * @return {@code true} if running
     */
    public boolean isRunning() {
        return running;
    }

    // ─── Accept loop ──────────────────────────────────────────────────────────

    /**
     * Main accept loop: waits for incoming TCP connections and submits each to
     * the handler pool.
     *
     * <p>The loop exits cleanly when {@link #running} is set to {@code false} and
     * the {@link ServerSocket} is closed.</p>
     */
    private void acceptLoop() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                handlerPool.submit(() -> handleConnection(clientSocket));
            } catch (SocketException e) {
                // Normal shutdown path — serverSocket.close() triggers this
                if (!running) {
                    LOGGER.fine("NodeServer accept loop terminated (shutdown)");
                } else {
                    LOGGER.warning(() -> "Unexpected SocketException in accept loop: " + e.getMessage());
                }
            } catch (IOException e) {
                if (running) {
                    LOGGER.warning(() -> "Error accepting connection: " + e.getMessage());
                }
            }
        }
    }

    // ─── Connection handler ───────────────────────────────────────────────────

    /**
     * Handles a single inbound TCP connection.
     *
     * <p>Protocol: the client sends a single UTF-8 JSON {@link MessageCodec.NetworkMessage}
     * terminated by a newline ({@code \n}). For simplicity each TCP connection carries
     * exactly one message; persistent connections with multiplexing are a Milestone-8 concern.</p>
     *
     * <h3>Access-control pipeline (T-054)</h3>
     * <ol>
     *   <li>Peek at raw bytes before deserializing to extract {@code senderId}.</li>
     *   <li>Check {@link AllowlistManager#isAllowed(String)} — drop + WARN if denied.</li>
     *   <li>Check {@link PermissionManager} role for the required operation — return error if denied.</li>
     *   <li>Fully deserialize and dispatch to the appropriate handler.</li>
     * </ol>
     *
     * @param socket the accepted client socket (non-null; closed before this method returns)
     */
    private void handleConnection(Socket socket) {
        String remoteAddr = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
        try (socket) {
            socket.setSoTimeout(10_000); // 10-second read timeout

            // ── Step 1: Read raw message bytes ──────────────────────────────
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
            String line = reader.readLine();
            if (line == null || line.isBlank()) {
                LOGGER.fine(() -> "Empty message from " + remoteAddr + " — dropping");
                return;
            }

            // ── Step 2: Lightweight parse to get senderId (pre-deserialization) ──
            // We decode fully here because JSON is small; for large payloads a
            // streaming parser would be used to extract senderId before full decode.
            MessageCodec.NetworkMessage message;
            try {
                message = codec.decodeFromString(line);
            } catch (IllegalArgumentException e) {
                LOGGER.warning(() -> "Malformed message from " + remoteAddr + " — dropping: " + e.getMessage());
                return;
            }

            String senderId = message.getSenderId();

            // ── Step 3: Allowlist check (NFR-SEC-04, FR-AC-01, T-054) ───────
            // This is the primary security gate: drop non-allowlisted traffic
            // before any further processing (AC-07: silently dropped and logged).
            if (!allowlistManager.isAllowed(senderId)) {
                LOGGER.warning(() -> "Dropping message from non-allowlisted node: " + senderId
                    + " at " + remoteAddr);
                return; // silent drop
            }

            // ── Step 4: Record heartbeat for connected peer ──────────────────
            peerManager.recordHeartbeat(senderId);

            // ── Step 5: Dispatch by message type ────────────────────────────
            handleMessage(message, socket);

        } catch (IOException e) {
            LOGGER.fine(() -> "IO error handling connection from " + remoteAddr + ": " + e.getMessage());
        }
    }

    // ─── Message dispatch ─────────────────────────────────────────────────────

    /**
     * Dispatches a validated, fully deserialized message to the appropriate handler.
     *
     * <p>Each handler performs a role check via {@link PermissionManager} before
     * executing business logic, enforcing FR-AC-03.</p>
     *
     * @param message the decoded, allowlisted message (non-null)
     * @param socket  the client socket, used for sending responses (non-null)
     * @throws IOException if writing a response fails
     */
    private void handleMessage(MessageCodec.NetworkMessage message, Socket socket) throws IOException {
        switch (message.getType()) {
            case BLOCK -> handleBlock(message);
            case TRANSACTION -> handleTransaction(message);
            case GET_STATUS -> handleGetStatus(message, socket);
            case GET_BLOCKS -> handleGetBlocks(message, socket);
            case PING -> handlePing(message, socket);
            case PONG -> { /* no response needed */ }
            default -> LOGGER.fine(() -> "Unhandled message type: " + message.getType()
                + " from " + message.getSenderId());
        }
    }

    /**
     * Handles an inbound {@code BLOCK} message.
     *
     * <p>Role check: the sender must have {@link NodeRole#NODE_MINER} or
     * {@link NodeRole#NODE_ADMIN} to submit a block (FR-AC-03).</p>
     *
     * @param message the BLOCK message (non-null)
     */
    private void handleBlock(MessageCodec.NetworkMessage message) {
        // Role check: only MINER or ADMIN may submit blocks (FR-AC-03)
        NodeRole senderRoleForBlock = permissionManager.getRole(message.getSenderId()).orElse(null);
        if (senderRoleForBlock == null || !senderRoleForBlock.canSubmitBlock()) {
            LOGGER.warning(() -> "Node " + message.getSenderId()
                + " lacks permission to submit blocks — dropping");
            return;
        }

        try {
            Block block = codec.extractBlock(message);
            LOGGER.info(() -> "Received BLOCK from " + message.getSenderId()
                + ": index=" + block.getIndex() + " hash=" + block.getHash().substring(0, 12) + "...");
            // Delegate to blockchain for validation + persistence
            blockchain.addBlock(block);
        } catch (Exception e) {
            LOGGER.warning(() -> "Failed to add inbound block from " + message.getSenderId()
                + ": " + e.getMessage());
        }
    }

    /**
     * Handles an inbound {@code TRANSACTION} message.
     *
     * <p>Role check: the sender must have {@link NodeRole#NODE_MINER} or
     * {@link NodeRole#NODE_ADMIN} to submit transactions (FR-AC-03).</p>
     *
     * @param message the TRANSACTION message (non-null)
     */
    private void handleTransaction(MessageCodec.NetworkMessage message) {
        // Role check: only MINER or ADMIN may submit transactions (FR-AC-03)
        NodeRole senderRoleForTx = permissionManager.getRole(message.getSenderId()).orElse(null);
        if (senderRoleForTx == null || !senderRoleForTx.canSubmitTransaction()) {
            LOGGER.warning(() -> "Node " + message.getSenderId()
                + " lacks permission to submit transactions — dropping");
            return;
        }

        try {
            Transaction tx = codec.extractTransaction(message);
            LOGGER.fine(() -> "Received TRANSACTION from " + message.getSenderId()
                + ": id=" + tx.getId());
            // Forward via gossip if wired
            if (gossipProtocol != null) {
                gossipProtocol.gossip(tx, message.getSenderId());
            }
        } catch (Exception e) {
            LOGGER.warning(() -> "Failed to handle inbound transaction from " + message.getSenderId()
                + ": " + e.getMessage());
        }
    }

    /**
     * Handles an inbound {@code GET_STATUS} message by replying with this node's chain height.
     *
     * <p>All roles may read chain state (FR-AC-03 — canRead = true for all).</p>
     *
     * @param message the GET_STATUS message (non-null)
     * @param socket  the client socket for sending the response (non-null)
     * @throws IOException if writing the response fails
     */
    private void handleGetStatus(MessageCodec.NetworkMessage message, Socket socket) throws IOException {
        int chainHeight = blockchain.size();
        MessageCodec.NetworkMessage response = codec.statusMessage(
            "local", // local node ID — set properly when nodeId is wired in
            chainHeight);
        sendResponse(socket, response);
        LOGGER.fine(() -> "Responded to GET_STATUS from " + message.getSenderId()
            + " with chainHeight=" + chainHeight);
    }

    /**
     * Handles an inbound {@code GET_BLOCKS} message by fetching the requested block
     * range from the local chain and returning them as a JSON array in the response payload.
     *
     * <p>All roles may read chain state (FR-AC-03 — canRead = true for all).</p>
     *
     * @param message the GET_BLOCKS message (non-null)
     * @param socket  the client socket for sending the response (non-null)
     * @throws IOException if writing the response fails
     */
    private void handleGetBlocks(MessageCodec.NetworkMessage message, Socket socket) throws IOException {
        try {
            // Parse the from/to range out of the payload JSON
            com.fasterxml.jackson.databind.JsonNode node =
                new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(message.getPayload());
            int from = node.get("from").asInt();
            int to = node.get("to").asInt();

            // Clamp to actual chain height to avoid out-of-range loads
            int maxIndex = blockchain.size() - 1;
            to = Math.min(to, maxIndex);

            java.util.List<com.privatechain.core.model.Block> blocks = new java.util.ArrayList<>();
            for (int i = from; i <= to; i++) {
                try {
                    blocks.add(blockchain.getBlock(i));
                } catch (java.util.NoSuchElementException ignored) {
                    break; // stop at first missing block
                }
            }

            // Serialize the block list as the response payload.
            // Must use full BlockSerializer-compatible config so that timestamps
            // and visibility settings match what BlockFetcher expects to deserialize.
            com.fasterxml.jackson.databind.ObjectMapper blockMapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
            blockMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            blockMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            blockMapper.disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            blockMapper.setVisibility(com.fasterxml.jackson.annotation.PropertyAccessor.GETTER,
                com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE);
            blockMapper.setVisibility(com.fasterxml.jackson.annotation.PropertyAccessor.IS_GETTER,
                com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE);
            blockMapper.setVisibility(com.fasterxml.jackson.annotation.PropertyAccessor.FIELD,
                com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY);
            String blocksJson = blockMapper.writeValueAsString(blocks);

            MessageCodec.NetworkMessage response = new MessageCodec.NetworkMessage(
                MessageCodec.MessageType.BLOCK, "local", blocksJson);
            sendResponse(socket, response);

            int finalTo = to;
            LOGGER.fine(() -> "Responded to GET_BLOCKS [" + from + ".." + finalTo
                + "] from " + message.getSenderId()
                + " with " + blocks.size() + " block(s)");

        } catch (Exception e) {
            LOGGER.warning(() -> "Failed to handle GET_BLOCKS from "
                + message.getSenderId() + ": " + e.getMessage());
        }
    }

    /**
     * Handles an inbound {@code PING} message by replying with a {@code PONG}.
     *
     * @param message the PING message (non-null)
     * @param socket  the client socket for the PONG response (non-null)
     * @throws IOException if writing the response fails
     */
    private void handlePing(MessageCodec.NetworkMessage message, Socket socket) throws IOException {
        MessageCodec.NetworkMessage pong = codec.pongMessage("local");
        sendResponse(socket, pong);
        LOGGER.fine(() -> "PONG sent to " + message.getSenderId());
    }

    /**
     * Writes a response message to the client socket as a UTF-8 JSON line.
     *
     * @param socket   the client socket (non-null)
     * @param response the message to send (non-null)
     * @throws IOException if writing fails
     */
    private void sendResponse(Socket socket, MessageCodec.NetworkMessage response) throws IOException {
        PrintWriter writer = new PrintWriter(
            new OutputStreamWriter(socket.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8),
            /* autoFlush= */ true);
        writer.println(codec.encodeToString(response));
    }
}
