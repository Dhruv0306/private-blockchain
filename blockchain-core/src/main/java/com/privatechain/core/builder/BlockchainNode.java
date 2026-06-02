package com.privatechain.core.builder;

import com.privatechain.core.event.BlockchainEvent;
import com.privatechain.core.event.BlockchainEventBus;
import com.privatechain.core.exception.TransactionValidationException;
import com.privatechain.core.mempool.TransactionMempool;
import com.privatechain.core.model.Block;
import com.privatechain.core.model.Transaction;
import com.privatechain.core.network.ChainSyncer;
import com.privatechain.core.network.NodeServerLifecycle;
import com.privatechain.core.network.PeerManagerLifecycle;
import com.privatechain.core.spi.TransactionValidator;
import com.privatechain.core.spi.ValidationResult;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Top-level entry point for the private-blockchain library.
 *
 * <p>{@code BlockchainNode} orchestrates all subsystems — chain management, mempool,
 * event bus, P2P networking — through a well-defined lifecycle
 * ({@link #start()} / {@link #stop()}).</p>
 *
 * <h2>Minimum example (FR-CFG-02)</h2>
 * <pre>{@code
 * BlockchainNode node = BlockchainConfig.builder().build().start();
 * System.out.println(node.status().chainHeight());
 * node.stop();
 * }</pre>
 *
 * <h2>Milestone 8 changes (T-066)</h2>
 * <ul>
 *   <li>{@link TransactionMempool} is now a first-class dependency, started and stopped
 *       with the node lifecycle.</li>
 *   <li>{@link #status()} correctly reports live {@code mempoolSize}.</li>
 *   <li>All event listeners from {@link BlockchainConfig#getEventListeners()} are wired
 *       onto the shared {@link BlockchainEventBus} during construction.</li>
 *   <li>Network subsystem setters are now {@code public} so that {@code blockchain-network}
 *       can wire in {@link NodeServerLifecycle}, {@link PeerManagerLifecycle}, and
 *       {@link ChainSyncer} from outside this package.</li>
 * </ul>
 *
 * @see BlockchainConfig
 * @see Blockchain
 * @since 1.0.0
 */
public final class BlockchainNode {

    private static final Logger LOGGER = Logger.getLogger(BlockchainNode.class.getName());

    // ─── Wired subsystems ─────────────────────────────────────────────────────

    private final BlockchainConfig config;
    private final Blockchain blockchain;

    /**
     * Shared event bus — always non-null; created by {@link BlockchainConfig.Builder}
     * before the node is constructed.
     */
    private final BlockchainEventBus eventBus;

    /**
     * Transaction mempool wired to the event bus (Milestone 8 — T-066).
     * Publishes {@link BlockchainEvent.TransactionSubmittedEvent} on submit and
     * removes confirmed transactions on {@link BlockchainEvent.BlockAddedEvent}.
     */
    private final TransactionMempool mempool;

    // ─── Optional Milestone 7 network subsystems ──────────────────────────────
    // Public setters allow blockchain-network (a different Maven module / package)
    // to inject implementations while keeping blockchain-core dependency-free.

    /**
     * TCP server for inbound peer connections.
     * {@code null} when the node runs in standalone (no-network) mode.
     */
    private NodeServerLifecycle nodeServer;

    /**
     * Manages the peer lifecycle (connect / disconnect / heartbeat).
     * {@code null} when the node runs in standalone mode.
     */
    private PeerManagerLifecycle peerManager;

    /**
     * Performs an initial chain synchronization on startup.
     * {@code null} when the node runs in standalone mode.
     */
    private ChainSyncer syncManager;

    // ─── Lifecycle state ──────────────────────────────────────────────────────

    /**
     * Guards against double-start or operations before {@link #start()}.
     */
    private final AtomicBoolean started = new AtomicBoolean(false);

    // ─── Constructor ──────────────────────────────────────────────────────────

    /**
     * Constructs a {@code BlockchainNode} from the given configuration.
     *
     * <p>Construction is lightweight — it does not start networking or mine blocks.
     * Call {@link #start()} to begin operations. The event bus is always non-null
     * because {@link BlockchainConfig.Builder} initialises it unconditionally.</p>
     *
     * @param config the fully built configuration (non-null)
     * @throws NullPointerException if config is null
     */
    public BlockchainNode(BlockchainConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.eventBus = config.getEventBus(); // never null — builder always sets it

        // ── Wire chain manager ─────────────────────────────────────────────────
        this.blockchain = new Blockchain(
            config.getConsensusEngine(),
            config.getStorage(),
            eventBus);

        // ── Wire TransactionMempool with EventBus (Milestone 8 — T-066) ───────
        // The mempool registers a BlockAddedEvent listener internally so confirmed
        // transactions are removed without any additional wiring here.
        this.mempool = new TransactionMempool(
            config.getTransactionPrioritizer(),
            eventBus);

        LOGGER.info(() ->
            "BlockchainNode constructed [chainId=" + config.getChainId()
                + ", engine=" + config.getConsensusEngine().engineName() + "]");
    }

    // ─── Network subsystem injection ──────────────────────────────────────────
    // Public so blockchain-network (different package/module) can inject impls.

    /**
     * Wires the node server into this node.
     *
     * <p>Called by {@code blockchain-network} during assembly. Applications should
     * not call this directly.</p>
     *
     * @param nodeServer the server to bind on {@link #start()}; may be {@code null}
     *                   for standalone mode
     */
    public void setNodeServer(NodeServerLifecycle nodeServer) {
        this.nodeServer = nodeServer;
    }

    /**
     * Wires the peer manager into this node.
     *
     * <p>Called by {@code blockchain-network} during assembly.</p>
     *
     * @param peerManager the peer manager to start/stop with this node;
     *                    may be {@code null} for standalone mode
     */
    public void setPeerManager(PeerManagerLifecycle peerManager) {
        this.peerManager = peerManager;
    }

    /**
     * Wires the chain syncer into this node.
     *
     * <p>Called by {@code blockchain-network} during assembly.</p>
     *
     * @param syncManager the syncer to invoke on startup;
     *                    may be {@code null} for standalone mode
     */
    public void setSyncManager(ChainSyncer syncManager) {
        this.syncManager = syncManager;
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    /**
     * Starts the blockchain node.
     *
     * <p>Steps in order:</p>
     * <ol>
     *   <li>Genesis block if chain is empty (FR-CORE-07).</li>
     *   <li>Mempool TTL eviction scheduler (Milestone 8).</li>
     *   <li>{@link PeerManagerLifecycle} heartbeat (Milestone 7).</li>
     *   <li>{@link NodeServerLifecycle} TCP bind (Milestone 7).</li>
     *   <li>Initial chain sync via {@link ChainSyncer} (Milestone 7).</li>
     * </ol>
     *
     * @return this node (for fluent use)
     * @throws IllegalStateException if already started
     */
    public BlockchainNode start() {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("BlockchainNode is already started");
        }

        LOGGER.info(() -> "Starting BlockchainNode [chainId=" + config.getChainId()
            + ", engine=" + config.getConsensusEngine().engineName()
            + ", port=" + config.getNetworkPort() + "]");

        // Step 1: genesis
        if (blockchain.isEmpty()) {
            Block genesis = GenesisBlockFactory.create(config.getChainId());
            blockchain.addBlock(genesis);
            LOGGER.info(() -> "Genesis block created: " + genesis.getHash());
        } else {
            LOGGER.info(() -> "Resuming chain at height " + blockchain.size());
        }

        // Step 2: mempool eviction (Milestone 8 — T-066)
        mempool.start(config.getMempoolTtl());
        LOGGER.info(() -> "TransactionMempool started [ttl=" + config.getMempoolTtl() + "]");

        // Step 3: peer manager heartbeat (Milestone 7)
        if (peerManager != null) {
            peerManager.start();
            LOGGER.info("PeerManager started");
        }

        // Step 4: node server bind (Milestone 7)
        if (nodeServer != null) {
            nodeServer.start();
            LOGGER.info(() -> "NodeServer bound on port " + config.getNetworkPort());
        }

        // Step 5: initial sync (Milestone 7)
        if (syncManager != null) {
            int appended = syncManager.syncChain();
            if (appended > 0) {
                LOGGER.info(() -> "Initial sync complete — appended " + appended + " block(s)");
            } else {
                LOGGER.info("Initial sync: local chain is up-to-date");
            }
        }

        LOGGER.info(() ->
            "BlockchainNode started successfully [height=" + blockchain.size() + "]");
        return this;
    }

    /**
     * Stops the blockchain node and cleanly releases all subsystems.
     *
     * <p>Shutdown order (reverse of startup):</p>
     * <ol>
     *   <li>Stop {@link NodeServerLifecycle}</li>
     *   <li>Stop {@link PeerManagerLifecycle}</li>
     *   <li>Stop mempool eviction scheduler (Milestone 8)</li>
     *   <li>Shutdown {@link BlockchainEventBus}</li>
     * </ol>
     *
     * @throws IllegalStateException if not yet started
     */
    public void stop() {
        if (!started.get()) {
            throw new IllegalStateException("BlockchainNode has not been started");
        }
        LOGGER.info("Stopping BlockchainNode...");

        if (nodeServer != null && nodeServer.isRunning()) {
            nodeServer.stop();
            LOGGER.info("NodeServer stopped");
        }
        if (peerManager != null) {
            peerManager.stop();
            LOGGER.info("PeerManager stopped");
        }

        // Milestone 8: stop mempool before shutting down the bus
        mempool.stop();
        LOGGER.info("TransactionMempool stopped");

        eventBus.shutdown();
        LOGGER.info("BlockchainNode stopped");
    }

    // ─── Transaction API ──────────────────────────────────────────────────────

    /**
     * Validates and submits a transaction through the configured validator chain.
     *
     * <p>Each registered {@link TransactionValidator} is applied in sequence.
     * On success the transaction is added to the mempool which publishes a
     * {@link BlockchainEvent.TransactionSubmittedEvent} (T-066).</p>
     *
     * @param transaction the transaction to submit (non-null)
     * @throws NullPointerException           if transaction is null
     * @throws TransactionValidationException if any validator rejects the transaction
     * @throws IllegalStateException          if the node has not been started
     */
    public void submitTransaction(Transaction transaction) {
        requireStarted();
        Objects.requireNonNull(transaction, "transaction must not be null");

        for (TransactionValidator validator : config.getTransactionValidators()) {
            ValidationResult result = validator.validate(transaction, blockchain);
            if (result.isFailure()) {
                throw new TransactionValidationException(
                    "Transaction " + transaction.getId() + " rejected: " + result.getErrors(),
                    result,
                    transaction);
            }
        }

        // submit() publishes TransactionSubmittedEvent internally (Milestone 8 T-066)
        mempool.submit(transaction);
        LOGGER.fine(() -> "Transaction submitted [id=" + transaction.getId()
            + ", mempoolSize=" + mempool.size() + "]");
    }

    // ─── Chain queries ────────────────────────────────────────────────────────

    /**
     * Returns the underlying {@link Blockchain} for direct chain access.
     *
     * @return non-null {@link Blockchain}
     */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "Blockchain is the intentional public API; thread-safe via ReadWriteLock.")
    public Blockchain getChain() {
        return blockchain;
    }

    /**
     * Returns the transaction mempool managed by this node.
     *
     * @return non-null {@link TransactionMempool}
     */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "TransactionMempool is the intentional public API; thread-safe internally.")
    public TransactionMempool getMempool() {
        return mempool;
    }

    // ─── Status ───────────────────────────────────────────────────────────────

    /**
     * Returns a snapshot of the current node status (NFR-UX-04).
     *
     * <p>The {@code mempoolSize} field is populated from the live
     * {@link TransactionMempool} (fixed in Milestone 8; was always {@code 0} before).</p>
     *
     * @return non-null {@link NodeStatus}
     * @throws IllegalStateException if the node has not been started
     */
    public NodeStatus status() {
        requireStarted();
        Block latest = blockchain.isEmpty() ? null : blockchain.getLatestBlock();
        int peers = (peerManager != null) ? peerManager.getConnectedPeerCount() : 0;
        return new NodeStatus(
            blockchain.size(),
            mempool.size(),      // M8: live mempool size (was 0 before)
            peers,
            latest == null ? null : latest.getHeader().timestamp(),
            config.getConsensusEngine().engineName());
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Asserts that the node has been started.
     *
     * @throws IllegalStateException if not started
     */
    private void requireStarted() {
        if (!started.get()) {
            throw new IllegalStateException(
                "BlockchainNode must be started before this operation. Call start() first.");
        }
    }

    // ─── NodeStatus record ────────────────────────────────────────────────────

    /**
     * Immutable snapshot of a node's operational status (NFR-UX-04).
     *
     * @param chainHeight     number of blocks in the canonical chain
     * @param mempoolSize     number of unconfirmed transactions in the mempool
     * @param peerCount       number of currently connected peers (0 in standalone mode)
     * @param lastBlockTime   timestamp of the most recently added block; {@code null} if empty
     * @param consensusEngine human-readable name of the active consensus engine
     * @since 1.0.0
     */
    public record NodeStatus(
        int chainHeight,
        int mempoolSize,
        int peerCount,
        Instant lastBlockTime,
        String consensusEngine) {
    }
}
