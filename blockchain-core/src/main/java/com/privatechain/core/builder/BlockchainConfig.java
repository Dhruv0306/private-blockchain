package com.privatechain.core.builder;

import com.privatechain.core.event.BlockchainEventBus;
import com.privatechain.core.event.BlockchainEventListener;
import com.privatechain.core.spi.BlockchainStorage;
import com.privatechain.core.spi.ConsensusEngine;
import com.privatechain.core.spi.TransactionPrioritizer;
import com.privatechain.core.spi.TransactionValidator;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Fluent builder and single assembly point for all blockchain subsystems.
 *
 * <p>{@code BlockchainConfig} is the <em>only</em> place where modules are composed
 * (design.md §1 — Explicit Wiring). No global state, no service-locator, no classpath
 * scanning. Every dependency is provided explicitly via the builder (FR-CFG-01).</p>
 *
 * <h2>Minimum viable setup (FR-CFG-02)</h2>
 * <pre>{@code
 * BlockchainNode node = BlockchainConfig.builder().build();
 * node.start();
 * }</pre>
 *
 * <h2>Milestone 8 additions (T-066)</h2>
 * <p>Two new builder parameters control the mempool lifecycle wired into
 * {@link BlockchainNode}:</p>
 * <ul>
 *   <li>{@link Builder#mempoolTtl(Duration)} — TTL for unconfirmed transactions
 *       (default: 30 minutes)</li>
 *   <li>{@link Builder#maxMempoolSize(int)} — max pool capacity
 *       (default: unbounded)</li>
 * </ul>
 *
 * @see BlockchainNode
 * @since 1.0.0
 */
public final class BlockchainConfig {

    // ─── Wired dependencies ───────────────────────────────────────────────────

    private final ConsensusEngine consensusEngine;
    private final List<TransactionValidator> transactionValidators;
    private final BlockchainStorage storage;
    private final BlockchainEventBus eventBus;
    private final List<BlockchainEventListener> eventListeners;
    private final TransactionPrioritizer transactionPrioritizer;

    // ─── Tunable parameters ───────────────────────────────────────────────────

    private final int networkPort;
    private final int blockTimeSeconds;
    private final int difficulty;
    private final int maxPeers;
    private final String chainId;

    // ─── Mempool parameters (Milestone 8 — T-066) ────────────────────────────

    /**
     * TTL for unconfirmed transactions in the mempool.
     */
    private final Duration mempoolTtl;

    /**
     * Maximum number of transactions the mempool can hold.
     */
    private final int maxMempoolSize;

    // ─── Private constructor ──────────────────────────────────────────────────

    /**
     * Private — use {@link #builder()} to construct instances.
     *
     * @param builder the populated builder
     */
    private BlockchainConfig(Builder builder) {
        this.consensusEngine = builder.consensusEngine;
        this.transactionValidators = List.copyOf(builder.transactionValidators);
        this.storage = builder.storage;
        this.eventBus = builder.eventBus;
        this.eventListeners = List.copyOf(builder.eventListeners);
        this.transactionPrioritizer = builder.transactionPrioritizer;
        this.networkPort = builder.networkPort;
        this.blockTimeSeconds = builder.blockTimeSeconds;
        this.difficulty = builder.difficulty;
        this.maxPeers = builder.maxPeers;
        this.chainId = builder.chainId;
        this.mempoolTtl = builder.mempoolTtl;
        this.maxMempoolSize = builder.maxMempoolSize;
    }

    // ─── Factory ──────────────────────────────────────────────────────────────

    /**
     * Returns a new, pre-populated {@link Builder} with production-ready defaults.
     *
     * @return a mutable builder
     */
    public static Builder builder() {
        return new Builder();
    }

    // ─── Accessors ────────────────────────────────────────────────────────────

    /**
     * Returns the configured consensus engine.
     *
     * @return non-null {@link ConsensusEngine}
     */
    public ConsensusEngine getConsensusEngine() {
        return consensusEngine;
    }

    /**
     * Returns the ordered list of transaction validators.
     *
     * @return non-null, possibly empty, unmodifiable list
     */
    public List<TransactionValidator> getTransactionValidators() {
        return transactionValidators;
    }

    /**
     * Returns the configured storage backend.
     *
     * @return non-null {@link BlockchainStorage}
     */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "BlockchainStorage is a public SPI intentionally exposed. "
            + "Field is final; SPI contract defines thread safety.")
    public BlockchainStorage getStorage() {
        return storage;
    }

    /**
     * Returns the shared event bus instance. Always non-null.
     *
     * @return non-null {@link BlockchainEventBus}
     */
    public BlockchainEventBus getEventBus() {
        return eventBus;
    }

    /**
     * Returns the list of event listeners pre-registered during configuration.
     *
     * @return non-null, possibly empty, unmodifiable list
     */
    public List<BlockchainEventListener> getEventListeners() {
        return eventListeners;
    }

    /**
     * Returns the configured transaction prioritizer.
     *
     * @return non-null {@link TransactionPrioritizer}
     */
    public TransactionPrioritizer getTransactionPrioritizer() {
        return transactionPrioritizer;
    }

    /**
     * Returns the TCP port on which the P2P node server listens.
     *
     * @return port number (1024–65535)
     */
    public int getNetworkPort() {
        return networkPort;
    }

    /**
     * Returns the target time (in seconds) between consecutive blocks.
     *
     * @return block time in seconds (&ge; 1)
     */
    public int getBlockTimeSeconds() {
        return blockTimeSeconds;
    }

    /**
     * Returns the PoW difficulty (leading zero bits required in a valid hash).
     *
     * @return difficulty (&ge; 1)
     */
    public int getDifficulty() {
        return difficulty;
    }

    /**
     * Returns the maximum number of simultaneous peer connections.
     *
     * @return max peers (&ge; 1)
     */
    public int getMaxPeers() {
        return maxPeers;
    }

    /**
     * Returns the stable chain identifier used to create the genesis block.
     *
     * @return non-null, non-blank chain ID
     */
    public String getChainId() {
        return chainId;
    }

    /**
     * Returns the TTL for unconfirmed transactions in the mempool (Milestone 8).
     *
     * @return non-null, positive duration
     */
    public Duration getMempoolTtl() {
        return mempoolTtl;
    }

    /**
     * Returns the maximum number of transactions the mempool can hold (Milestone 8).
     *
     * @return max mempool size (&ge; 1)
     */
    public int getMaxMempoolSize() {
        return maxMempoolSize;
    }

    // ─── Builder ──────────────────────────────────────────────────────────────

    /**
     * Fluent builder for {@link BlockchainConfig}.
     *
     * <p>All fields have production-ready defaults so that {@code builder().build()}
     * produces a working in-memory chain (FR-CFG-02).</p>
     */
    public static final class Builder {

        // Resolved lazily in build() to avoid circular deps with consensus/storage modules
        private ConsensusEngine consensusEngine;
        private BlockchainStorage storage;

        private final List<TransactionValidator> transactionValidators = new ArrayList<>();
        private final List<BlockchainEventListener> eventListeners = new ArrayList<>();

        // eventBus is always initialized — BlockchainNode may rely on non-null guarantee
        private BlockchainEventBus eventBus = new BlockchainEventBus();

        // Prioritizer: null until build(); resolved to a timestamp-ordering lambda
        // (same pattern as existing codebase — avoids importing mempool classes here)
        private TransactionPrioritizer transactionPrioritizer;

        private int networkPort = 8545;
        private int blockTimeSeconds = 10;
        private int difficulty = 4;
        private int maxPeers = 25;
        private String chainId = "private-blockchain";

        // Mempool parameters (Milestone 8 — T-066)
        private Duration mempoolTtl = Duration.ofMinutes(30);
        private int maxMempoolSize = Integer.MAX_VALUE;

        /**
         * Package-private constructor — use {@link BlockchainConfig#builder()}.
         */
        Builder() {
        }

        /**
         * Sets the consensus engine.
         *
         * <p>If not set, defaults to a no-op engine (accepts all blocks).
         * Production chains must supply a real engine.</p>
         *
         * @param engine the engine to use (non-null)
         * @return this builder
         * @throws NullPointerException if engine is null
         */
        public Builder consensusEngine(ConsensusEngine engine) {
            this.consensusEngine = Objects.requireNonNull(engine, "engine must not be null");
            return this;
        }

        /**
         * Appends a transaction validator to the validation chain (FR-TX-03).
         *
         * @param validator the validator to add (non-null)
         * @return this builder
         * @throws NullPointerException if validator is null
         */
        public Builder transactionValidator(TransactionValidator validator) {
            transactionValidators.add(
                Objects.requireNonNull(validator, "validator must not be null"));
            return this;
        }

        /**
         * Sets the storage backend.
         *
         * <p>If not set, defaults to in-memory storage (ephemeral).</p>
         *
         * @param storage the persistence backend (non-null)
         * @return this builder
         * @throws NullPointerException if storage is null
         */
        public Builder storage(BlockchainStorage storage) {
            this.storage = Objects.requireNonNull(storage, "storage must not be null");
            return this;
        }

        /**
         * Replaces the default event bus with a custom instance.
         *
         * @param eventBus the event bus to use (non-null)
         * @return this builder
         * @throws NullPointerException if eventBus is null
         */
        public Builder eventBus(BlockchainEventBus eventBus) {
            this.eventBus = Objects.requireNonNull(eventBus, "eventBus must not be null");
            return this;
        }

        /**
         * Registers an event listener that will receive all blockchain events.
         *
         * @param listener the listener to register (non-null)
         * @return this builder
         * @throws NullPointerException if listener is null
         */
        public Builder eventListener(BlockchainEventListener listener) {
            eventListeners.add(Objects.requireNonNull(listener, "listener must not be null"));
            return this;
        }

        /**
         * Sets the transaction prioritizer for mempool ordering (FR-MEMPOOL-02).
         *
         * @param prioritizer the ordering strategy (non-null)
         * @return this builder
         * @throws NullPointerException if prioritizer is null
         */
        public Builder transactionPrioritizer(TransactionPrioritizer prioritizer) {
            this.transactionPrioritizer =
                Objects.requireNonNull(prioritizer, "prioritizer must not be null");
            return this;
        }

        /**
         * Sets the TCP port for the P2P node server.
         *
         * @param port port number (1024–65535)
         * @return this builder
         * @throws IllegalArgumentException if port is out of range
         */
        public Builder networkPort(int port) {
            if (port < 1024 || port > 65535) {
                throw new IllegalArgumentException(
                    "Port must be in range [1024, 65535], got: " + port);
            }
            this.networkPort = port;
            return this;
        }

        /**
         * Sets the target block time in seconds.
         *
         * @param seconds block time (&ge; 1)
         * @return this builder
         * @throws IllegalArgumentException if seconds &lt; 1
         */
        public Builder blockTimeSeconds(int seconds) {
            if (seconds < 1) {
                throw new IllegalArgumentException(
                    "blockTimeSeconds must be >= 1, got: " + seconds);
            }
            this.blockTimeSeconds = seconds;
            return this;
        }

        /**
         * Sets the PoW difficulty (number of leading zero bits).
         *
         * @param difficulty difficulty level (&ge; 1)
         * @return this builder
         * @throws IllegalArgumentException if difficulty &lt; 1
         */
        public Builder difficulty(int difficulty) {
            if (difficulty < 1) {
                throw new IllegalArgumentException(
                    "difficulty must be >= 1, got: " + difficulty);
            }
            this.difficulty = difficulty;
            return this;
        }

        /**
         * Sets the maximum number of simultaneous peer connections.
         *
         * @param maxPeers max peers (&ge; 1)
         * @return this builder
         * @throws IllegalArgumentException if maxPeers &lt; 1
         */
        public Builder maxPeers(int maxPeers) {
            if (maxPeers < 1) {
                throw new IllegalArgumentException(
                    "maxPeers must be >= 1, got: " + maxPeers);
            }
            this.maxPeers = maxPeers;
            return this;
        }

        /**
         * Sets the stable chain identifier embedded in the genesis block.
         *
         * @param chainId non-null, non-blank identifier
         * @return this builder
         * @throws NullPointerException     if chainId is null
         * @throws IllegalArgumentException if chainId is blank
         */
        public Builder chainId(String chainId) {
            Objects.requireNonNull(chainId, "chainId must not be null");
            if (chainId.isBlank()) {
                throw new IllegalArgumentException("chainId must not be blank");
            }
            this.chainId = chainId;
            return this;
        }

        /**
         * Sets the TTL for unconfirmed transactions in the mempool (Milestone 8).
         *
         * <p>Default: 30 minutes.</p>
         *
         * @param ttl positive duration (non-null)
         * @return this builder
         * @throws NullPointerException     if ttl is null
         * @throws IllegalArgumentException if ttl is zero or negative
         */
        public Builder mempoolTtl(Duration ttl) {
            Objects.requireNonNull(ttl, "mempoolTtl must not be null");
            if (ttl.isZero() || ttl.isNegative()) {
                throw new IllegalArgumentException("mempoolTtl must be positive, got: " + ttl);
            }
            this.mempoolTtl = ttl;
            return this;
        }

        /**
         * Sets the maximum pool capacity (Milestone 8).
         *
         * <p>Default: unbounded ({@link Integer#MAX_VALUE}).</p>
         *
         * @param maxSize max pool capacity (&ge; 1)
         * @return this builder
         * @throws IllegalArgumentException if maxSize &lt; 1
         */
        public Builder maxMempoolSize(int maxSize) {
            if (maxSize < 1) {
                throw new IllegalArgumentException(
                    "maxMempoolSize must be >= 1, got: " + maxSize);
            }
            this.maxMempoolSize = maxSize;
            return this;
        }

        /**
         * Builds the configuration and returns a ready-to-start {@link BlockchainNode}.
         *
         * <p>Lazy defaults are resolved here so that {@code blockchain-core} never
         * hard-imports classes from {@code blockchain-consensus} or
         * {@code blockchain-storage}.</p>
         *
         * @return a configured, not-yet-started {@link BlockchainNode}
         */
        public BlockchainNode build() {
            applyDefaults();
            for (BlockchainEventListener listener : eventListeners) {
                eventBus.register(listener);
            }
            BlockchainConfig config = new BlockchainConfig(this);
            return new BlockchainNode(config);
        }

        /**
         * Builds and returns the raw {@link BlockchainConfig} without wrapping it in a node.
         *
         * <p>Useful for testing or DI containers that construct the node separately.</p>
         *
         * @return a fully configured, immutable {@link BlockchainConfig}
         */
        public BlockchainConfig buildConfig() {
            applyDefaults();
            for (BlockchainEventListener listener : eventListeners) {
                eventBus.register(listener);
            }
            return new BlockchainConfig(this);
        }

        /**
         * Resolves all lazy defaults before construction.
         *
         * <p>Using a lambda for the prioritizer default avoids importing
         * {@code TimestampBasedPrioritizer} here, keeping the builder free of
         * mempool-package dependencies (same pattern as existing codebase).</p>
         */
        private void applyDefaults() {
            if (consensusEngine == null) {
                consensusEngine = new NoOpConsensusEngine();
            }
            if (storage == null) {
                storage = new InMemoryBlockchainStorage();
            }
            if (transactionPrioritizer == null) {
                // Default: FIFO by timestamp — same lambda as existing BlockchainConfig
                transactionPrioritizer =
                    (t1, t2) -> t1.getTimestamp().compareTo(t2.getTimestamp());
            }
        }
    }

    // ─── Default inner implementations ───────────────────────────────────────
    // Keep blockchain-core free of any external dependency (design.md §7.1).

    /**
     * Permissive no-op consensus engine — accepts every block unconditionally.
     * Used as default when no engine is configured. NOT FOR PRODUCTION.
     */
    private static final class NoOpConsensusEngine implements ConsensusEngine {

        /**
         * Accepts all blocks unconditionally.
         *
         * @param block the candidate block (ignored)
         * @param chain the current chain (ignored)
         * @return always {@code true}
         */
        @Override
        public boolean validateBlock(
            com.privatechain.core.model.Block block, Blockchain chain) {
            return true;
        }

        /**
         * Creates a minimal block with no mining effort.
         *
         * @param transactions  transactions to include
         * @param previousBlock the current chain tip
         * @return a new block linked to previousBlock
         */
        @Override
        public com.privatechain.core.model.Block mineBlock(
            List<com.privatechain.core.model.Transaction> transactions,
            com.privatechain.core.model.Block previousBlock) {
            com.privatechain.core.model.BlockHeader header =
                com.privatechain.core.model.BlockHeader.builder()
                    .nonce(0L)
                    .merkleRoot(com.privatechain.core.model.BlockHeader.EMPTY_MERKLE_ROOT)
                    .build();
            return com.privatechain.core.model.Block.builder()
                .index(previousBlock.getIndex() + 1)
                .previousHash(previousBlock.getHash())
                .transactions(transactions)
                .header(header)
                .build();
        }

        /**
         * Returns the engine name.
         *
         * @return {@code "NoOp"}
         */
        @Override
        public String engineName() {
            return "NoOp";
        }
    }

    /**
     * In-memory storage backed by a {@link java.util.LinkedHashMap}.
     * Data is lost on JVM exit. NOT FOR PRODUCTION.
     */
    private static final class InMemoryBlockchainStorage implements BlockchainStorage {

        private final java.util.Map<Integer, com.privatechain.core.model.Block> store =
            new java.util.LinkedHashMap<>();
        private final java.util.concurrent.locks.ReadWriteLock lock =
            new java.util.concurrent.locks.ReentrantReadWriteLock();

        private static boolean constantTimeHashEquals(String a, String b) {
            return java.security.MessageDigest.isEqual(
                a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        /**
         * Saves a block to the in-memory store.
         *
         * @param block the block to persist (non-null)
         */
        @Override
        public void saveBlock(com.privatechain.core.model.Block block) {
            lock.writeLock().lock();
            try {
                store.put(block.getIndex(), block);
            } finally {
                lock.writeLock().unlock();
            }
        }

        /**
         * Loads a block by index.
         *
         * @param index block index
         * @return the stored block
         * @throws java.util.NoSuchElementException if not found
         */
        @Override
        public com.privatechain.core.model.Block loadBlock(int index) {
            lock.readLock().lock();
            try {
                com.privatechain.core.model.Block b = store.get(index);
                if (b == null) {
                    throw new java.util.NoSuchElementException("No block at index " + index);
                }
                return b;
            } finally {
                lock.readLock().unlock();
            }
        }

        /**
         * Loads a block by hash.
         *
         * @param hash the block hash to search for
         * @return Optional containing the block if found
         */
        @Override
        public java.util.Optional<com.privatechain.core.model.Block> loadBlockByHash(
            String hash) {
            lock.readLock().lock();
            try {
                return store.values().stream()
                    .filter(b -> constantTimeHashEquals(b.getHash(), hash))
                    .findFirst();
            } finally {
                lock.readLock().unlock();
            }
        }

        /**
         * Returns all blocks in insertion order.
         *
         * @return list of all blocks
         */
        @Override
        public List<com.privatechain.core.model.Block> loadAll() {
            lock.readLock().lock();
            try {
                return new java.util.ArrayList<>(store.values());
            } finally {
                lock.readLock().unlock();
            }
        }

        /**
         * Checks whether a block with the given hash exists.
         *
         * @param hash the hash to look up
         * @return true if present
         */
        @Override
        public boolean exists(String hash) {
            lock.readLock().lock();
            try {
                return store.values().stream()
                    .anyMatch(b -> constantTimeHashEquals(b.getHash(), hash));
            } finally {
                lock.readLock().unlock();
            }
        }

        /**
         * Returns the number of stored blocks.
         *
         * @return block count
         */
        @Override
        public int chainHeight() {
            lock.readLock().lock();
            try {
                return store.size();
            } finally {
                lock.readLock().unlock();
            }
        }

        /**
         * Removes all stored blocks.
         */
        @Override
        public void deleteAll() {
            lock.writeLock().lock();
            try {
                store.clear();
            } finally {
                lock.writeLock().unlock();
            }
        }
    }
}
