package com.privatechain.core.builder;

import com.privatechain.core.event.BlockchainEventBus;
import com.privatechain.core.event.BlockchainEventListener;
import com.privatechain.core.spi.BlockchainStorage;
import com.privatechain.core.spi.ConsensusEngine;
import com.privatechain.core.spi.TransactionPrioritizer;
import com.privatechain.core.spi.TransactionValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Fluent builder and single assembly point for all blockchain subsystems.
 *
 * <p>{@code BlockchainConfig} is the <em>only</em> place where modules are composed
 * (design.md §1 — Explicit Wiring). There is no global state, no service-locator,
 * and no classpath scanning. Every dependency is provided explicitly via the builder
 * (FR-CFG-01).</p>
 *
 * <h2>Minimum viable setup (FR-CFG-02)</h2>
 * <pre>{@code
 * // Zero-config: in-memory PoW chain with sensible defaults
 * BlockchainNode node = BlockchainConfig.builder().build();
 * node.start();
 * }</pre>
 *
 * <h2>Full configuration</h2>
 * <pre>{@code
 * BlockchainNode node = BlockchainConfig.builder()
 *     .consensusEngine(new ProofOfAuthorityEngine(authorizedAddresses))
 *     .transactionValidator(new SignatureTransactionValidator())
 *     .transactionValidator(new BalanceValidator())   // chained automatically
 *     .storage(new LevelDBStorage("/data/chain"))
 *     .networkPort(8545)
 *     .blockTimeSeconds(5)
 *     .chainId("acme-supply-chain-v1")
 *     .eventListener(myAuditLogger)
 *     .build();
 * }</pre>
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
    }

    // ─── Factory ──────────────────────────────────────────────────────────────

    /**
     * Returns a new, pre-populated {@link Builder} with in-memory PoW defaults.
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
     * <p>Validators are applied in the order they were registered via
     * {@link Builder#transactionValidator(TransactionValidator)}.</p>
     *
     * @return non-null, possibly empty, unmodifiable list
     */
    public List<TransactionValidator> getTransactionValidators() {
        return transactionValidators;
    }

    /**
     * Returns the configured storage backend.
     *
     * <p>SpotBugs flags this as {@code EI_EXPOSE_REP} because the returned reference
     * is mutable. This is intentional: {@link BlockchainStorage} is a public SPI
     * designed to be shared with the {@link Blockchain} chain manager and sync components.
     * The field is {@code private final}; the SPI contract governs thread safety.</p>
     *
     * @return non-null {@link BlockchainStorage}
     */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "BlockchainStorage is a public SPI intentionally exposed to "
            + "the chain manager. Field is final; SPI contract defines thread safety.")
    public BlockchainStorage getStorage() {
        return storage;
    }

    /**
     * Returns the shared event bus instance.
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
     * Returns the PoW difficulty (number of leading zero bits required in a valid hash).
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

    // ─── Builder ──────────────────────────────────────────────────────────────

    /**
     * Fluent builder for {@link BlockchainConfig}.
     *
     * <p>All fields have production-ready defaults so that {@code builder().build()}
     * produces a working in-memory chain (FR-CFG-02).</p>
     */
    public static final class Builder {

        private final List<TransactionValidator> transactionValidators = new ArrayList<>();
        private final List<BlockchainEventListener> eventListeners = new ArrayList<>();
        // Defaults: in-memory no-op consensus and no-op storage are set here as nulls
        // and replaced by real defaults during build() to avoid circular dependencies
        // with the consensus/storage modules that blockchain-core must not depend on.
        private ConsensusEngine consensusEngine;
        private BlockchainStorage storage;
        private BlockchainEventBus eventBus = new BlockchainEventBus();
        private TransactionPrioritizer transactionPrioritizer;

        // Tunable parameters with sensible defaults
        private int networkPort = 8545;
        private int blockTimeSeconds = 10;
        private int difficulty = 4;
        private int maxPeers = 25;
        private String chainId = "private-blockchain";

        /**
         * Package-private constructor — use {@link BlockchainConfig#builder()}.
         */
        Builder() {
        }

        /**
         * Sets the consensus engine.
         *
         * <p>If not set, the default behavior is to accept all blocks (no-op engine),
         * which is suitable for testing. Production chains must supply a real engine.</p>
         *
         * @param consensusEngine the engine to use (non-null)
         * @return this builder
         * @throws NullPointerException if consensusEngine is null
         */
        public Builder consensusEngine(ConsensusEngine consensusEngine) {
            this.consensusEngine = Objects.requireNonNull(consensusEngine, "consensusEngine must not be null");
            return this;
        }

        /**
         * Adds a transaction validator to the validation chain.
         *
         * <p>Validators are applied in the order they are added. The first validator
         * to return a failure result short-circuits the chain.</p>
         *
         * @param validator the validator to add (non-null)
         * @return this builder
         * @throws NullPointerException if validator is null
         */
        public Builder transactionValidator(TransactionValidator validator) {
            transactionValidators.add(Objects.requireNonNull(validator, "validator must not be null"));
            return this;
        }

        /**
         * Sets the persistence backend.
         *
         * <p>If not set, the built node uses an in-memory storage implementation,
         * which is ephemeral across JVM restarts.</p>
         *
         * @param storage the storage implementation to use (non-null)
         * @return this builder
         * @throws NullPointerException if storage is null
         */
        public Builder storage(BlockchainStorage storage) {
            this.storage = Objects.requireNonNull(storage, "storage must not be null");
            return this;
        }

        /**
         * Replaces the default event bus with a custom one.
         *
         * <p>Useful in testing to pre-populate listeners or to share the bus
         * across multiple components.</p>
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
         * Registers an event listener to receive blockchain events.
         *
         * <p>All listeners registered here are automatically registered on the event bus
         * during {@link #build()}.</p>
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
         * Sets the transaction prioritizer used by the mempool.
         *
         * @param prioritizer the prioritizer to use (non-null)
         * @return this builder
         * @throws NullPointerException if prioritizer is null
         */
        public Builder transactionPrioritizer(TransactionPrioritizer prioritizer) {
            this.transactionPrioritizer = Objects.requireNonNull(prioritizer, "prioritizer must not be null");
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
                throw new IllegalArgumentException("Port must be in range [1024, 65535], got: " + port);
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
                throw new IllegalArgumentException("blockTimeSeconds must be >= 1, got: " + seconds);
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
                throw new IllegalArgumentException("difficulty must be >= 1, got: " + difficulty);
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
                throw new IllegalArgumentException("maxPeers must be >= 1, got: " + maxPeers);
            }
            this.maxPeers = maxPeers;
            return this;
        }

        /**
         * Sets the stable chain identifier (used to generate the genesis block).
         *
         * @param chainId non-null, non-blank chain ID
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
         * Builds the configuration and returns a ready-to-start {@link BlockchainNode}.
         *
         * <p>This is the primary factory method for assembling a node. If
         * {@code consensusEngine} or {@code storage} was not provided, sensible
         * in-memory / no-op defaults are applied automatically (FR-CFG-02). All
         * registered event listeners are wired to the event bus before the node is
         * constructed.</p>
         *
         * <pre>{@code
         * // Zero-config: in-memory PoW chain
         * BlockchainNode node = BlockchainConfig.builder().build();
         * node.start();
         * }</pre>
         *
         * @return a configured, not-yet-started {@link BlockchainNode}
         */
        public BlockchainNode build() {
            // Apply defaults for optional dependencies
            if (consensusEngine == null) {
                consensusEngine = new NoOpConsensusEngine();
            }
            if (storage == null) {
                storage = new InMemoryBlockchainStorage();
            }
            if (transactionPrioritizer == null) {
                transactionPrioritizer = (t1, t2) -> t1.getTimestamp().compareTo(t2.getTimestamp());
            }

            // Pre-register all listeners on the event bus
            for (BlockchainEventListener listener : eventListeners) {
                eventBus.register(listener);
            }

            BlockchainConfig config = new BlockchainConfig(this);
            return new BlockchainNode(config);
        }

        /**
         * Builds and returns the raw {@link BlockchainConfig} without wrapping it in a node.
         *
         * <p>Use this variant when you need to inspect or share the configuration object
         * before constructing a node, e.g., for testing or dependency-injection containers.</p>
         *
         * @return a fully configured, immutable {@link BlockchainConfig}
         */
        public BlockchainConfig buildConfig() {
            if (consensusEngine == null) {
                consensusEngine = new NoOpConsensusEngine();
            }
            if (storage == null) {
                storage = new InMemoryBlockchainStorage();
            }
            if (transactionPrioritizer == null) {
                transactionPrioritizer = (t1, t2) -> t1.getTimestamp().compareTo(t2.getTimestamp());
            }
            for (BlockchainEventListener listener : eventListeners) {
                eventBus.register(listener);
            }
            return new BlockchainConfig(this);
        }
    }

    // ─── Default no-op implementations (inner classes) ────────────────────────
    // These live here to keep blockchain-core free of any external dependency.
    // Real implementations are in blockchain-consensus and blockchain-storage.

    // ─── Static imports needed by inner classes ───────────────────────────────
    // (Compiler resolves simple names within the outer class, but inner classes
    //  that reference model types need fully-qualified names OR the outer class
    //  must import them — we add explicit FQN usages inside the inner classes.)

    /**
     * A permissive no-op consensus engine that accepts every block without further checks.
     * Used as the default when no engine is configured.
     *
     * <p><strong>NOT FOR PRODUCTION</strong> — provides no security guarantees.</p>
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
        public boolean validateBlock(com.privatechain.core.model.Block block, Blockchain chain) {
            return true;
        }

        /**
         * Creates a new block with the given transactions without any mining.
         *
         * @param transactions  transactions to include
         * @param previousBlock the current chain tip
         * @return a new block linked to {@code previousBlock}
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
         * Returns the name of this engine.
         *
         * @return {@code "NoOp"}
         */
        @Override
        public String engineName() {
            return "NoOp";
        }
    }

    /**
     * In-memory storage backed by a {@link java.util.LinkedHashMap} keyed by block index.
     * Provided as a default when no storage is configured; data is lost on JVM exit.
     *
     * <p><strong>NOT FOR PRODUCTION</strong> — use {@code LevelDBStorage} or
     * {@code RocksDBStorage} for durable chains.</p>
     */
    private static final class InMemoryBlockchainStorage implements BlockchainStorage {

        private final java.util.Map<Integer, com.privatechain.core.model.Block> store =
            new java.util.LinkedHashMap<>();
        private final java.util.concurrent.locks.ReadWriteLock lock =
            new java.util.concurrent.locks.ReentrantReadWriteLock();

        /**
         * Compares two hex-encoded hash strings in constant time to prevent
         * timing side-channel attacks (NFR-SEC-03).
         *
         * <p>Uses {@link java.security.MessageDigest#isEqual(byte[], byte[])} which
         * is guaranteed by the JDK to run in time proportional to the length of the
         * arrays rather than the position of the first differing byte.</p>
         *
         * @param a first hash hex string
         * @param b second hash hex string
         * @return {@code true} if both strings represent the same hash value
         */
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
                com.privatechain.core.model.Block block = store.get(index);
                if (block == null) {
                    throw new java.util.NoSuchElementException("No block at index " + index);
                }
                return block;
            } finally {
                lock.readLock().unlock();
            }
        }

        /**
         * Loads a block by hash using a constant-time comparison to prevent timing attacks.
         *
         * @param hash the block hash
         * @return an Optional containing the block if found
         */
        @Override
        public java.util.Optional<com.privatechain.core.model.Block> loadBlockByHash(String hash) {
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
         * Returns all blocks in ascending index order.
         *
         * @return ordered list of all blocks
         */
        @Override
        public List<com.privatechain.core.model.Block> loadAll() {
            lock.readLock().lock();
            try {
                return new ArrayList<>(store.values());
            } finally {
                lock.readLock().unlock();
            }
        }

        /**
         * Checks if a block with the given hash exists, using a constant-time
         * comparison to prevent hash timing attacks.
         *
         * @param hash block hash to check
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
