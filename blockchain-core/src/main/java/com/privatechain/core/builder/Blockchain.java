package com.privatechain.core.builder;

import com.privatechain.core.event.BlockchainEvent;
import com.privatechain.core.event.BlockchainEventBus;
import com.privatechain.core.exception.BlockValidationException;
import com.privatechain.core.model.Block;
import com.privatechain.core.spi.BlockchainStorage;
import com.privatechain.core.spi.ConsensusEngine;
import org.apache.logging.log4j.internal.annotation.SuppressFBWarnings;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

/**
 * The canonical chain manager — orchestrates block appending, chain validation,
 * and event publication.
 *
 * <p>{@code Blockchain} is the single source of truth for the current state of the
 * ledger. It delegates persistence to {@link BlockchainStorage}, consensus checks to
 * {@link ConsensusEngine}, and state-change notifications to {@link BlockchainEventBus}
 * (FR-CORE-04).</p>
 *
 * <h2>Thread safety</h2>
 * <p>All public methods are thread-safe:</p>
 * <ul>
 *   <li>{@link #addBlock(Block)} acquires an exclusive write lock before appending.</li>
 *   <li>All read operations ({@link #getBlock}, {@link #getLatestBlock}, {@link #size},
 *       {@link #isChainValid}) use a shared read lock.</li>
 *   <li>Multiple readers may proceed concurrently; writers are exclusive (design.md §7.4).</li>
 * </ul>
 *
 * <h2>Chain integrity</h2>
 * <p>On startup (when the chain is loaded from storage), callers should invoke
 * {@link #isChainValid()} to detect any corruption. {@link #addBlock(Block)} performs
 * incremental validation (hash linkage, Merkle root, consensus) on every new block.</p>
 *
 * @see BlockchainConfig
 * @see BlockchainNode
 * @since 1.0.0
 */
public final class Blockchain {

    private static final Logger LOGGER = Logger.getLogger(Blockchain.class.getName());

    // ─── Dependencies (injected via BlockchainConfig) ─────────────────────────

    private final ConsensusEngine consensusEngine;
    private final BlockchainStorage storage;
    private final BlockchainEventBus eventBus;

    // ─── Concurrency control ──────────────────────────────────────────────────

    /**
     * ReadWriteLock allows many concurrent reads but enforces exclusive writes.
     * Using a fair lock (true) prevents writer starvation under heavy read load.
     */
    private final ReadWriteLock chainLock = new ReentrantReadWriteLock(true);

    // ─── Constructor ──────────────────────────────────────────────────────────

    /**
     * Constructs a {@code Blockchain} with the given dependencies.
     *
     * <p>Loads any previously persisted blocks from {@code storage} and appends them
     * to the in-memory view. If storage is empty (first run), the genesis block is
     * expected to be added explicitly by {@link BlockchainNode} during startup.</p>
     *
     * @param consensusEngine the engine used to validate and produce blocks (non-null)
     * @param storage         the persistence backend (non-null)
     * @param eventBus        the event bus for publishing state-change events (non-null)
     * @throws NullPointerException if any argument is null
     */
    public Blockchain(ConsensusEngine consensusEngine, BlockchainStorage storage,
                      BlockchainEventBus eventBus) {
        this.consensusEngine = Objects.requireNonNull(consensusEngine, "consensusEngine must not be null");
        this.storage = Objects.requireNonNull(storage, "storage must not be null");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus must not be null");
        LOGGER.info(() -> "Blockchain initialised with engine: " + consensusEngine.engineName()
            + ", chainHeight: " + storage.chainHeight());
    }

    // ─── Block management ─────────────────────────────────────────────────────

    /**
     * Appends a new block to the chain after performing all validation checks.
     *
     * <p>Validation sequence:</p>
     * <ol>
     *   <li>Block hash integrity — {@code block.isHashValid()} must return {@code true}</li>
     *   <li>Chain linkage — {@code block.getPreviousHash()} must equal the latest block's hash</li>
     *   <li>Block index — must equal {@code latestBlock.getIndex() + 1}</li>
     *   <li>Consensus validation — {@link ConsensusEngine#validateBlock(Block, Blockchain)}</li>
     * </ol>
     *
     * <p>On success, the block is persisted to {@link BlockchainStorage} and a
     * {@link BlockchainEvent.BlockAddedEvent} is published asynchronously to
     * all registered listeners.</p>
     *
     * @param block the candidate block to append (non-null)
     * @throws NullPointerException     if block is null
     * @throws BlockValidationException if any validation step fails
     */
    public void addBlock(Block block) {
        Objects.requireNonNull(block, "block must not be null");

        chainLock.writeLock().lock();
        try {
            validateNewBlock(block);

            // Persist first — if storage throws, the in-memory state is unchanged
            storage.saveBlock(block);

            LOGGER.info(() -> "Block #" + block.getIndex() + " added. Hash: " + block.getHash());
        } finally {
            chainLock.writeLock().unlock();
        }

        // Publish event outside the lock to avoid holding the lock during listener dispatch
        eventBus.publish(new BlockchainEvent.BlockAddedEvent(block));
    }

    /**
     * Performs all pre-append validation checks on a candidate block.
     *
     * <p>Must be called while the write lock is held.</p>
     *
     * @param block the block to validate
     * @throws BlockValidationException on any validation failure
     */
    private void validateNewBlock(Block block) {
        // 1. Hash integrity: recompute and compare
        if (!block.isHashValid()) {
            throw new BlockValidationException(
                "Block hash is invalid — the block may have been tampered with. "
                    + "Block index: " + block.getIndex(), block);
        }

        // 2. Chain linkage and index ordering
        if (storage.chainHeight() > 0) {
            Block latest = storage.loadBlock(storage.chainHeight() - 1);

            if (!block.getPreviousHash().equals(latest.getHash())) {
                throw new BlockValidationException(
                    "Block previousHash " + block.getPreviousHash()
                        + " does not match latest block hash " + latest.getHash(), block);
            }

            if (block.getIndex() != latest.getIndex() + 1) {
                throw new BlockValidationException(
                    "Expected block index " + (latest.getIndex() + 1)
                        + " but got " + block.getIndex(), block);
            }
        } else {
            // Chain is empty — only genesis (index 0) is valid
            if (block.getIndex() != 0) {
                throw new BlockValidationException(
                    "Chain is empty but block index is " + block.getIndex()
                        + "; expected 0 (genesis)", block);
            }
            if (!Block.GENESIS_PREVIOUS_HASH.equals(block.getPreviousHash())) {
                throw new BlockValidationException(
                    "Genesis block previousHash must be all zeros", block);
            }
        }

        // 3. Consensus engine validation (PoW difficulty, PoA signature, etc.)
        if (!consensusEngine.validateBlock(block, this)) {
            throw new BlockValidationException(
                "Block rejected by consensus engine [" + consensusEngine.engineName() + "]", block);
        }
    }

    // ─── Queries ──────────────────────────────────────────────────────────────

    /**
     * Returns the block at the given index.
     *
     * @param index block index (&ge; 0)
     * @return the block at {@code index}
     * @throws NoSuchElementException if no block exists at that index
     */
    public Block getBlock(int index) {
        chainLock.readLock().lock();
        try {
            return storage.loadBlock(index);
        } finally {
            chainLock.readLock().unlock();
        }
    }

    /**
     * Returns the block with the given hash, if present.
     *
     * @param hash hex-encoded block hash (non-null)
     * @return an {@link Optional} containing the block, or empty if not found
     */
    public Optional<Block> getBlockByHash(String hash) {
        Objects.requireNonNull(hash, "hash must not be null");
        chainLock.readLock().lock();
        try {
            return storage.loadBlockByHash(hash);
        } finally {
            chainLock.readLock().unlock();
        }
    }

    /**
     * Returns the most recently added block (the chain tip).
     *
     * @return the latest block
     * @throws NoSuchElementException if the chain is empty
     */
    public Block getLatestBlock() {
        chainLock.readLock().lock();
        try {
            int height = storage.chainHeight();
            if (height == 0) {
                throw new NoSuchElementException("Chain is empty — no latest block");
            }
            return storage.loadBlock(height - 1);
        } finally {
            chainLock.readLock().unlock();
        }
    }

    /**
     * Returns all blocks in ascending index order.
     *
     * <p>For large chains, iterating by index range is preferred over loading all blocks
     * into memory at once.</p>
     *
     * @return non-null, possibly empty list of all blocks
     */
    public List<Block> getChain() {
        chainLock.readLock().lock();
        try {
            return storage.loadAll();
        } finally {
            chainLock.readLock().unlock();
        }
    }

    /**
     * Returns the number of blocks currently in the chain.
     *
     * @return chain size (&ge; 0)
     */
    public int size() {
        chainLock.readLock().lock();
        try {
            return storage.chainHeight();
        } finally {
            chainLock.readLock().unlock();
        }
    }

    /**
     * Returns {@code true} if the chain contains no blocks.
     *
     * @return {@code true} when {@link #size()} == 0
     */
    public boolean isEmpty() {
        return size() == 0;
    }

    // ─── Chain integrity ──────────────────────────────────────────────────────

    /**
     * Verifies the full integrity of the blockchain without network access (FR-CORE-06).
     *
     * <p>Checks for each block (starting from index 1):</p>
     * <ol>
     *   <li>The stored hash matches a fresh computation (tamper detection)</li>
     *   <li>The {@code previousHash} equals the hash of the preceding block</li>
     * </ol>
     *
     * <p>An empty chain or a single-block chain (genesis only) is always considered valid.</p>
     *
     * @return {@code true} if every block passes both checks
     */
    public boolean isChainValid() {
        chainLock.readLock().lock();
        try {
            List<Block> chain = storage.loadAll();

            if (chain.size() <= 1) {
                return true;
            }

            for (int i = 1; i < chain.size(); i++) {
                Block current = chain.get(i);
                Block previous = chain.get(i - 1);

                // Check that the current block's stored hash is consistent with its fields
                if (!current.isHashValid()) {
                    LOGGER.warning(() -> "Hash mismatch at block index " + current.getIndex());
                    return false;
                }

                // Check the chain linkage
                if (!current.getPreviousHash().equals(previous.getHash())) {
                    LOGGER.warning(() -> "Chain broken between block "
                        + previous.getIndex() + " and " + current.getIndex());
                    return false;
                }
            }
            return true;
        } finally {
            chainLock.readLock().unlock();
        }
    }

    // ─── Accessors for internal modules ───────────────────────────────────────

    /**
     * Returns the consensus engine used by this chain.
     *
     * @return non-null {@link ConsensusEngine}
     */
    public ConsensusEngine getConsensusEngine() {
        return consensusEngine;
    }

    /**
     * Returns the storage backend used by this chain.
     *
     * <p>SpotBugs flags this as {@code EI_EXPOSE_REP} because the returned reference
     * is mutable. This is intentional: {@link BlockchainStorage} is a public SPI whose
     * methods are called by modules that legitimately need write access. The field is {@code private
     * final}, so the reference itself cannot be replaced; callers are responsible for
     * not bypassing the concurrency contract defined in the SPI Javadoc.</p>
     *
     * @return non-null {@link BlockchainStorage}
     */
    @SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "BlockchainStorage is a public SPI intentionally shared with "
            + "authorised internal modules. The field is final; the SPI contract "
            + "defines the thread-safety requirements for callers.")
    public BlockchainStorage getStorage() {
        return storage;
    }
}
