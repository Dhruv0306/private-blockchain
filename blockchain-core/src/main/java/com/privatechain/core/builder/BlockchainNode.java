package com.privatechain.core.builder;

import com.privatechain.core.event.BlockchainEvent;
import com.privatechain.core.event.BlockchainEventBus;
import com.privatechain.core.exception.TransactionValidationException;
import com.privatechain.core.model.Block;
import com.privatechain.core.model.Transaction;
import com.privatechain.core.network.ChainSyncer;
import com.privatechain.core.network.NodeServerLifecycle;
import com.privatechain.core.network.PeerManagerLifecycle;
import com.privatechain.core.spi.TransactionValidator;
import com.privatechain.core.spi.ValidationResult;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Top-level entry point for the private blockchain library.
 *
 * <p>{@code BlockchainNode} orchestrates all subsystems — the chain, event bus,
 * and (from Milestone 7 onward) the P2P network — via the configuration assembled
 * by {@link BlockchainConfig} (FR-CFG-01, design.md §3).</p>
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>Build via {@link BlockchainConfig#builder()}</li>
 *   <li>Call {@link #start()} — initializes the chain, adds genesis block if needed,
 *       starts the node server, and triggers chain sync (Milestone 7)</li>
 *   <li>Interact via {@link #submitTransaction}, {@link #getChain()}, {@link #status()}</li>
 *   <li>Call {@link #stop()} — cleanly shuts down all subsystems</li>
 * </ol>
 *
 * <h2>Minimum example (FR-CFG-02)</h2>
 * <pre>{@code
 * BlockchainNode node = BlockchainConfig.builder().build().start();
 * System.out.println("Chain height: " + node.status().chainHeight());
 * node.stop();
 * }</pre>
 *
 * <h2>Network wiring (Milestone 7)</h2>
 * <p>When a {@link NodeServerLifecycle} is supplied to the config, {@link #start()}
 * binds it to the configured port, starts the peer manager, and triggers an initial
 * chain synchronization via {@link ChainSyncer}.
 * These subsystems are optional — if not configured the node runs in standalone mode.</p>
 *
 * <h2>Dependency isolation</h2>
 * <p>{@code blockchain-core} must have zero mandatory runtime dependencies beyond the JDK.
 * The network subsystems are therefore referenced through the interfaces
 * {@link NodeServerLifecycle}, {@link PeerManagerLifecycle}, and {@link ChainSyncer},
 * all defined in this module. The concrete implementations live in
 * {@code blockchain-network} and are injected via the package-private setters below.</p>
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
    private final BlockchainEventBus eventBus;

    // ─── Optional Milestone 7 network subsystems ──────────────────────────────
    // Typed as core interfaces so blockchain-core stays dependency-free.
    // Set via package-private setters called by BlockchainConfig.build().

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
     * Call {@link #start()} to begin operations.</p>
     *
     * @param config the fully built configuration (non-null)
     * @throws NullPointerException if config is null
     */
    public BlockchainNode(BlockchainConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.eventBus = config.getEventBus();
        this.blockchain = new Blockchain(
            config.getConsensusEngine(),
            config.getStorage(),
            eventBus);
    }

    // ─── Network subsystem injection (package-private, called by BlockchainConfig) ──

    /**
     * Wires the node server into this node.
     *
     * <p>Package-private — called by {@link BlockchainConfig} during assembly.
     * Applications should not call this directly.</p>
     *
     * @param nodeServer the server to bind on {@link #start()}; may be {@code null}
     *                   for standalone mode
     */
    void setNodeServer(NodeServerLifecycle nodeServer) {
        this.nodeServer = nodeServer;
    }

    /**
     * Wires the peer manager into this node.
     *
     * <p>Package-private — called by {@link BlockchainConfig} during assembly.</p>
     *
     * @param peerManager the peer manager to start/stop with this node;
     *                    may be {@code null} for standalone mode
     */
    void setPeerManager(PeerManagerLifecycle peerManager) {
        this.peerManager = peerManager;
    }

    /**
     * Wires the chain syncer into this node.
     *
     * <p>Package-private — called by {@link BlockchainConfig} during assembly.</p>
     *
     * @param syncManager the syncer to invoke on startup;
     *                    may be {@code null} for standalone mode
     */
    void setSyncManager(ChainSyncer syncManager) {
        this.syncManager = syncManager;
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    /**
     * Starts the blockchain node.
     *
     * <p>Performs the following steps in order:</p>
     * <ol>
     *   <li>Adds the genesis block if the chain is empty (FR-CORE-07).</li>
     *   <li>Starts the {@link PeerManagerLifecycle} heartbeat scheduler
     *       if networking is configured.</li>
     *   <li>Binds the {@link NodeServerLifecycle} to the configured TCP port
     *       (Milestone 7, T-058).</li>
     *   <li>Triggers an initial chain synchronisation via {@link ChainSyncer}
     *       (Milestone 7, T-062).</li>
     * </ol>
     *
     * @return this node (for fluent use)
     * @throws IllegalStateException if {@code start()} has already been called
     */
    public BlockchainNode start() {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("BlockchainNode is already started");
        }

        LOGGER.info(() -> "Starting BlockchainNode [chainId=" + config.getChainId()
            + ", engine=" + config.getConsensusEngine().engineName()
            + ", port=" + config.getNetworkPort() + "]");

        // ── Step 1: Bootstrap genesis block ───────────────────────────────────
        if (blockchain.isEmpty()) {
            Block genesis = GenesisBlockFactory.create(config.getChainId());
            blockchain.addBlock(genesis);
            LOGGER.info(() -> "Genesis block created: " + genesis.getHash());
        } else {
            LOGGER.info(() -> "Resuming chain at height " + blockchain.size());
        }

        // ── Step 2: Start peer manager heartbeat (Milestone 7) ───────────────
        if (peerManager != null) {
            peerManager.start();
            LOGGER.info("PeerManager started");
        }

        // ── Step 3: Bind node server (Milestone 7, T-058) ────────────────────
        if (nodeServer != null) {
            nodeServer.start();
            LOGGER.info(() -> "NodeServer bound on port " + config.getNetworkPort());
        }

        // ── Step 4: Initial chain synchronization (Milestone 7, T-062) ───────
        if (syncManager != null) {
            int appended = syncManager.syncChain();
            if (appended > 0) {
                LOGGER.info(() -> "Initial sync complete — appended " + appended + " block(s)");
            } else {
                LOGGER.info("Initial sync: local chain is up-to-date");
            }
        }

        LOGGER.info("BlockchainNode started successfully");
        return this;
    }

    /**
     * Stops the blockchain node and cleanly releases all subsystems.
     *
     * <p>Shutdown order (reverse of startup):</p>
     * <ol>
     *   <li>Stop the {@link NodeServerLifecycle} (close server socket)</li>
     *   <li>Disconnect all peers via {@link PeerManagerLifecycle}</li>
     *   <li>Shut down the {@link BlockchainEventBus}</li>
     * </ol>
     *
     * @throws IllegalStateException if {@code start()} was never called
     */
    public void stop() {
        if (!started.get()) {
            throw new IllegalStateException("BlockchainNode has not been started");
        }

        LOGGER.info("Stopping BlockchainNode...");

        // ── Step 1: Stop network server ───────────────────────────────────────
        if (nodeServer != null && nodeServer.isRunning()) {
            nodeServer.stop();
            LOGGER.info("NodeServer stopped");
        }

        // ── Step 2: Disconnect all peers ──────────────────────────────────────
        if (peerManager != null) {
            peerManager.stop();
            LOGGER.info("PeerManager stopped");
        }

        // ── Step 3: Shutdown event bus ────────────────────────────────────────
        eventBus.shutdown();
        LOGGER.info("BlockchainNode stopped");
    }

    // ─── Transaction API ──────────────────────────────────────────────────────

    /**
     * Validates and submits a transaction through the configured validator chain.
     *
     * <p>Each registered {@link TransactionValidator} is applied in sequence.
     * The first failure short-circuits validation and throws
     * {@link TransactionValidationException}. On success the transaction is published
     * to the event bus; the GossipProtocol
     * (registered as a listener in Milestone 7) then forwards it to peers.</p>
     *
     * @param transaction the transaction to submit (non-null)
     * @throws NullPointerException           if transaction is null
     * @throws TransactionValidationException if any validator rejects the transaction
     * @throws IllegalStateException          if the node has not been started
     */
    public void submitTransaction(Transaction transaction) {
        requireStarted();
        Objects.requireNonNull(transaction, "transaction must not be null");

        List<TransactionValidator> validators = config.getTransactionValidators();
        for (TransactionValidator validator : validators) {
            ValidationResult result = validator.validate(transaction, blockchain);
            if (result.isFailure()) {
                throw new TransactionValidationException(
                    "Transaction " + transaction.getId() + " rejected by validator: "
                        + result.getErrors(),
                    result,
                    transaction);
            }
        }

        eventBus.publish(new BlockchainEvent.TransactionSubmittedEvent(transaction));
        LOGGER.fine(() -> "Transaction accepted and published: " + transaction.getId());
    }

    // ─── Chain queries ────────────────────────────────────────────────────────

    /**
     * Returns the underlying {@link Blockchain} for direct chain access.
     *
     * <p>SpotBugs flags this as {@code EI_EXPOSE_REP} because {@link Blockchain} is
     * mutable. This is intentional: {@link Blockchain} is the primary chain-management
     * API and is thread-safe internally via {@link java.util.concurrent.locks.ReadWriteLock}.</p>
     *
     * @return non-null {@link Blockchain}
     */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "Blockchain is the intentional public API. Thread-safe via ReadWriteLock.")
    public Blockchain getChain() {
        return blockchain;
    }

    // ─── Status ───────────────────────────────────────────────────────────────

    /**
     * Returns a snapshot of the current node status (NFR-UX-04).
     *
     * <p>The {@code peerCount} field is populated from {@link PeerManagerLifecycle}
     * when networking is configured; it returns 0 in standalone mode.</p>
     *
     * @return non-null {@link NodeStatus} with current metrics
     * @throws IllegalStateException if the node has not been started
     */
    public NodeStatus status() {
        requireStarted();
        Block latest = blockchain.isEmpty() ? null : blockchain.getLatestBlock();
        int peers = (peerManager != null) ? peerManager.getConnectedPeerCount() : 0;
        return new NodeStatus(
            blockchain.size(),
            0,       // mempoolSize — wired in Milestone 5
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
