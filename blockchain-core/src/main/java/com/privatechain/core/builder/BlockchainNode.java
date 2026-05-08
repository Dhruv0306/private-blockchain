package com.privatechain.core.builder;

import com.privatechain.core.event.BlockchainEventBus;
import com.privatechain.core.model.Block;
import com.privatechain.core.model.Transaction;
import com.privatechain.core.spi.TransactionValidator;
import com.privatechain.core.spi.ValidationResult;
import com.privatechain.core.exception.TransactionValidationException;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Top-level entry point for the private blockchain library.
 *
 * <p>{@code BlockchainNode} is the primary object a consuming application interacts with.
 * It orchestrates all subsystems — the chain, event bus, mempool placeholder, and
 * (in later milestones) the P2P network — via the configuration assembled by
 * {@link BlockchainConfig} (FR-CFG-01, design.md §3).</p>
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>Build via {@link BlockchainConfig#builder()}</li>
 *   <li>Call {@link #start()} — initializes the chain, adds genesis block if needed</li>
 *   <li>Interact via {@link #submitTransaction}, {@link #getChain()}, etc.</li>
 *   <li>Call {@link #stop()} — cleanly shuts down the event bus and networking</li>
 * </ol>
 *
 * <h2>Minimum example (FR-CFG-02)</h2>
 * <pre>{@code
 * BlockchainNode node = BlockchainConfig.builder().build();
 * node.start();
 * System.out.println("Chain height: " + node.status().chainHeight());
 * node.stop();
 * }</pre>
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

    // ─── Lifecycle state ──────────────────────────────────────────────────────

    /** Guards against double-start or operations before start(). */
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

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    /**
     * Starts the blockchain node.
     *
     * <p>On the first start of a new chain this method adds the genesis block.
     * On subsequent starts with a persistent storage backend, the genesis block
     * is already present and the chain resumes from its persisted state.</p>
     *
     * <p>In later milestones this method will also bind the P2P {@code NodeServer}
     * and initiate peer discovery via {@code SyncManager}.</p>
     *
     * @return this node (for method chaining)
     * @throws IllegalStateException if {@code start()} has already been called
     */
    public BlockchainNode start() {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("BlockchainNode is already started");
        }

        LOGGER.info(() -> "Starting BlockchainNode [chainId=" + config.getChainId()
            + ", engine=" + config.getConsensusEngine().engineName()
            + ", port=" + config.getNetworkPort() + "]");

        // Bootstrap: add genesis block if chain is empty (first run)
        if (blockchain.isEmpty()) {
            Block genesis = GenesisBlockFactory.create(config.getChainId());
            blockchain.addBlock(genesis);
            LOGGER.info(() -> "Genesis block created: " + genesis.getHash());
        } else {
            LOGGER.info(() -> "Resuming chain at height " + blockchain.size());
        }

        // TODO (Milestone 7): NodeServer.listen(config.getNetworkPort())
        // TODO (Milestone 7): PeerManager.connect(config.getSeedPeers())
        // TODO (Milestone 7): SyncManager.syncChain()

        LOGGER.info("BlockchainNode started successfully");
        return this;
    }

    /**
     * Stops the blockchain node and cleanly releases all resources.
     *
     * <p>Pending events in the event bus are flushed before the delivery executor
     * is terminated. In later milestones this method will also close all peer
     * connections and flush the mempool.</p>
     *
     * @throws IllegalStateException if {@code start()} was never called
     */
    public void stop() {
        if (!started.get()) {
            throw new IllegalStateException("BlockchainNode has not been started");
        }

        LOGGER.info("Stopping BlockchainNode...");

        // TODO (Milestone 7): NodeServer.shutdown()
        // TODO (Milestone 7): PeerManager.disconnectAll()

        eventBus.shutdown();
        LOGGER.info("BlockchainNode stopped");
    }

    // ─── Transaction API ──────────────────────────────────────────────────────

    /**
     * Validates and submits a transaction to the mempool.
     *
     * <p>Each registered {@link TransactionValidator} is applied in sequence.
     * The first failure short-circuits validation and throws
     * {@link TransactionValidationException}. On success the transaction is
     * accepted into the mempool (full mempool is implemented in Milestone 5).</p>
     *
     * @param transaction the transaction to submit (non-null)
     * @throws NullPointerException              if transaction is null
     * @throws TransactionValidationException    if any validator rejects the transaction
     * @throws IllegalStateException             if the node has not been started
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

        // TODO (Milestone 5): TransactionMempool.submit(transaction)
        // TODO (Milestone 5): GossipProtocol.gossip(transaction)

        LOGGER.fine(() -> "Transaction accepted: " + transaction.getId());
    }

    // ─── Chain queries ────────────────────────────────────────────────────────

    /**
     * Returns the underlying {@link Blockchain} for direct chain access.
     *
     * <p>Consumers should prefer the higher-level methods on {@code BlockchainNode};
     * direct chain access is provided for advanced use cases (e.g., ChainExporter,
     * custom validators that need to read chain state).</p>
     *
     * <p>SpotBugs flags this as {@code EI_EXPOSE_REP} because {@link Blockchain} is
     * a mutable object. This is intentional: {@code Blockchain} is the primary chain
     * management API and consumers legitimately need to call {@code addBlock()},
     * {@code isChainValid()}, etc. The field is {@code private final}; the
     * {@link Blockchain} class enforces its own thread-safety guarantees internally
     * via {@link java.util.concurrent.locks.ReadWriteLock}.</p>
     *
     * @return non-null {@link Blockchain}
     */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "Blockchain is the intentional public API for chain management. "
            + "It is thread-safe internally via ReadWriteLock. Field is final.")
    public Blockchain getChain() {
        return blockchain;
    }

    // ─── Status ───────────────────────────────────────────────────────────────

    /**
     * Returns a snapshot of the current node status (NFR-UX-04).
     *
     * @return non-null {@link NodeStatus} with current metrics
     * @throws IllegalStateException if the node has not been started
     */
    public NodeStatus status() {
        requireStarted();
        Block latest = blockchain.isEmpty() ? null : blockchain.getLatestBlock();
        return new NodeStatus(
            blockchain.size(),
            0,                          // mempoolSize: implemented in Milestone 5
            0,                          // peerCount:   implemented in Milestone 7
            latest == null ? null : latest.getHeader().timestamp(),
            config.getConsensusEngine().engineName());
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    /**
     * Asserts that the node has been started before allowing operations.
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
     * Immutable snapshot of a node's operational status.
     *
     * <p>Returned by {@link BlockchainNode#status()} and exposed via the REST/Spring
     * integration in later milestones.</p>
     *
     * @param chainHeight      number of blocks in the chain
     * @param mempoolSize      number of unconfirmed transactions in the mempool
     * @param peerCount        number of currently connected peers
     * @param lastBlockTime    timestamp of the most recently added block, or null if none
     * @param consensusEngine  human-readable name of the active consensus engine
     * @since 1.0.0
     */
    public record NodeStatus(
        int chainHeight,
        int mempoolSize,
        int peerCount,
        Instant lastBlockTime,
        String consensusEngine) { }
}
