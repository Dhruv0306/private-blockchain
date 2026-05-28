package com.privatechain.network.rpc;

import com.privatechain.network.peer.Peer;
import com.privatechain.network.peer.PeerManager;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Manages outbound TCP connections from this node to remote peers.
 *
 * <p>{@code NodeClient} is the counterpart to {@link NodeServer}: while the server
 * handles inbound messages, the client is responsible for sending messages to remote
 * nodes. It is used by {@link com.privatechain.network.gossip.BlockBroadcaster},
 * {@link com.privatechain.network.gossip.GossipProtocol}, and
 * {@link com.privatechain.network.sync.SyncManager} to push data to specific peers.</p>
 *
 * <h2>Connection model</h2>
 * <p>This implementation uses short-lived, per-message TCP connections. A connection
 * is opened, one message is written and (optionally) one response is read, then
 * the socket is closed. This simplifies resource management and avoids connection
 * state drift. A pooled persistent-connection model can be introduced in a future
 * milestone per ADR-001 (T-064).</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * NodeClient client = new NodeClient("local-node-id", peerManager, codec);
 *
 * // Fire-and-forget (no response expected)
 * client.send(peer, blockMessage);
 *
 * // Request-response
 * Optional<NetworkMessage> status = client.sendAndReceive(peer, getStatusMessage);
 * }</pre>
 *
 * <h2>Thread safety</h2>
 * <p>All methods are stateless and safe for concurrent use from multiple threads.</p>
 *
 * @see NodeServer
 * @see PeerManager
 * @since 1.0.0
 */
public final class NodeClient {

    private static final Logger LOGGER = Logger.getLogger(NodeClient.class.getName());

    /**
     * Default connect/read timeout in milliseconds.
     */
    private static final int CONNECT_TIMEOUT_MS = 3_000;
    private static final int READ_TIMEOUT_MS = 5_000;

    // ─── Fields ───────────────────────────────────────────────────────────────

    /**
     * This node's logical identifier, included in every outbound message.
     */
    private final String localNodeId;

    /**
     * Peer manager — used to record heartbeats on successful sends.
     */
    private final PeerManager peerManager;

    /**
     * Message codec for serializing outbound messages.
     */
    private final MessageCodec codec;

    // ─── Constructor ──────────────────────────────────────────────────────────

    /**
     * Constructs a {@code NodeClient}.
     *
     * @param localNodeId this node's stable logical identifier (non-null, non-blank)
     * @param peerManager the peer lifecycle manager (non-null)
     * @param codec       the message codec (non-null)
     * @throws NullPointerException     if any parameter is null
     * @throws IllegalArgumentException if localNodeId is blank
     */
    public NodeClient(String localNodeId, PeerManager peerManager, MessageCodec codec) {
        Objects.requireNonNull(localNodeId, "localNodeId must not be null");
        if (localNodeId.isBlank()) {
            throw new IllegalArgumentException("localNodeId must not be blank");
        }
        this.localNodeId = localNodeId;
        this.peerManager = Objects.requireNonNull(peerManager, "peerManager must not be null");
        this.codec = Objects.requireNonNull(codec, "codec must not be null");
    }

    // ─── Send (fire-and-forget) ────────────────────────────────────────────────

    /**
     * Sends a message to the given peer without waiting for a response.
     *
     * <p>The connection is opened, the message is written as a UTF-8 JSON line,
     * and the socket is closed. On failure the peer is not automatically removed
     * from the peer manager — higher-level components (e.g., the heartbeat pruner
     * in {@link PeerManager}) are responsible for lifecycle decisions.</p>
     *
     * @param peer    the target peer (non-null)
     * @param message the message to send (non-null)
     * @return {@code true} if the message was sent successfully; {@code false} on I/O error
     * @throws NullPointerException if peer or message is null
     */
    public boolean send(Peer peer, MessageCodec.NetworkMessage message) {
        Objects.requireNonNull(peer, "peer must not be null");
        Objects.requireNonNull(message, "message must not be null");

        try (Socket socket = openSocket(peer)) {
            writeMessage(socket, message);
            // Record successful contact
            peerManager.recordHeartbeat(peer.getNodeId());
            LOGGER.fine(() -> "Sent " + message.getType() + " to " + peer.getNodeId()
                + " at " + peer.getAddress());
            return true;
        } catch (IOException e) {
            LOGGER.warning(() -> "Failed to send " + message.getType()
                + " to " + peer.getNodeId() + " at " + peer.getAddress()
                + ": " + e.getMessage());
            return false;
        }
    }

    // ─── Send-and-receive ─────────────────────────────────────────────────────

    /**
     * Sends a message to the given peer and blocks until a response is received.
     *
     * <p>The connection is opened, the request is written, a response line is read
     * and decoded, and the socket is closed. Returns {@link Optional#empty()} if
     * the peer does not respond within the read timeout or if any I/O error occurs.</p>
     *
     * @param peer    the target peer (non-null)
     * @param message the request message to send (non-null)
     * @return the decoded response, or empty on timeout/error
     * @throws NullPointerException if peer or message is null
     */
    public Optional<MessageCodec.NetworkMessage> sendAndReceive(
        Peer peer, MessageCodec.NetworkMessage message) {

        Objects.requireNonNull(peer, "peer must not be null");
        Objects.requireNonNull(message, "message must not be null");

        try (Socket socket = openSocket(peer)) {
            writeMessage(socket, message);

            // Read one response line
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String line = reader.readLine();
            if (line == null || line.isBlank()) {
                LOGGER.fine(() -> "No response from " + peer.getNodeId()
                    + " for " + message.getType());
                return Optional.empty();
            }

            MessageCodec.NetworkMessage response = codec.decodeFromString(line);
            peerManager.recordHeartbeat(peer.getNodeId());
            return Optional.of(response);

        } catch (IOException e) {
            LOGGER.warning(() -> "Error in send-receive to " + peer.getNodeId()
                + " at " + peer.getAddress() + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Opens a TCP socket to the given peer with configured timeouts.
     *
     * @param peer the target peer (non-null)
     * @return the opened, connected socket
     * @throws IOException if the connection cannot be established
     */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "UNENCRYPTED_SOCKET",
        justification = "Plain TCP is intentional for Milestone 7. TLS transport is deferred "
            + "to the Netty migration (ADR-001, T-064). All nodes are on a trusted private "
            + "network; allowlist + ECDSA invitation tokens provide the identity layer.")
    private Socket openSocket(Peer peer) throws IOException {
        Socket socket = new Socket();
        socket.connect(
            new java.net.InetSocketAddress(peer.getHost(), peer.getPort()),
            CONNECT_TIMEOUT_MS);
        socket.setSoTimeout(READ_TIMEOUT_MS);
        return socket;
    }

    /**
     * Writes a message to the given socket as a UTF-8 JSON line (newline-terminated).
     *
     * @param socket  the target socket (non-null, open)
     * @param message the message to write (non-null)
     * @throws IOException if writing fails
     */
    private void writeMessage(Socket socket, MessageCodec.NetworkMessage message) throws IOException {
        PrintWriter writer = new PrintWriter(
            new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8),
            /* autoFlush= */ true);
        writer.println(codec.encodeToString(message));
    }
}
