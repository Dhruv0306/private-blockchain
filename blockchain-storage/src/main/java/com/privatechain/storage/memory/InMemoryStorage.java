package com.privatechain.storage.memory;

import com.privatechain.core.exception.BlockValidationException;
import com.privatechain.core.model.Block;
import com.privatechain.core.spi.BlockchainStorage;

import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HashMap-backed in-memory implementation of {@link BlockchainStorage}.
 *
 * <p>This implementation is intended for <strong>testing and demos only</strong>.
 * All data is lost when the JVM exits. For durable storage use
 * {@code LevelDBStorage}, {@code RocksDBStorage}, or {@code FileSystemStorage}.</p>
 *
 * <h2>Thread safety</h2>
 * <p>Concurrent reads are allowed via a {@link ReentrantReadWriteLock} read lock.
 * All writes acquire the exclusive write lock, serializing mutations as required
 * by FR-STOR-06 and NFR-REL-01.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * BlockchainNode node = BlockchainConfig.builder()
 *     .storage(new InMemoryStorage())
 *     .build();
 * }</pre>
 *
 * @see BlockchainStorage
 * @see com.privatechain.core.builder.BlockchainConfig
 * @since 1.0.0
 */
public final class InMemoryStorage implements BlockchainStorage {

    private static final Logger LOGGER = Logger.getLogger(InMemoryStorage.class.getName());

    /**
     * Primary block store keyed by block index.
     * {@link TreeMap} maintains keys in natural (ascending integer) order, so
     * {@link #loadAll()} always returns blocks sorted by index regardless of
     * insertion order — even when blocks arrive out of sequence during chain sync.
     */
    private final Map<Integer, Block> store = new TreeMap<>();

    /**
     * Guards concurrent access. Read lock allows parallel queries;
     * write lock serializes all mutations.
     */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    // ─── BlockchainStorage implementation ────────────────────────────────────

    /**
     * Compares two hex-encoded hash strings in constant time to prevent timing
     * side-channel attacks (NFR-SEC-03).
     *
     * <p>{@link MessageDigest#isEqual(byte[], byte[])} is mandated by the JDK
     * to run in time proportional to the array length, not the position of the
     * first differing byte.</p>
     *
     * @param a first hash hex string
     * @param b second hash hex string
     * @return {@code true} if both strings represent the same hash
     */
    private static boolean constantTimeHashEquals(String a, String b) {
        return MessageDigest.isEqual(
            a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
            b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Persists a block to the in-memory store.
     *
     * <p>Saving the same block index twice overwrites the previous entry —
     * this satisfies the idempotent contract of {@link BlockchainStorage#saveBlock}.</p>
     *
     * @param block the block to persist (non-null)
     * @throws NullPointerException     if {@code block} is null
     * @throws BlockValidationException if storage fails unexpectedly
     */
    @Override
    public void saveBlock(Block block) {
        Objects.requireNonNull(block, "block must not be null");
        lock.writeLock().lock();
        try {
            store.put(block.getIndex(), block);
            LOGGER.fine(() -> "Saved block at index " + block.getIndex()
                + " hash=" + block.getHash().substring(0, 16) + "...");
        } catch (Exception ex) {
            throw new BlockValidationException(
                "InMemoryStorage failed to save block at index " + block.getIndex(),
                ex, null);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Retrieves a block by its zero-based index.
     *
     * @param index block index (&ge; 0)
     * @return the stored {@link Block}
     * @throws NoSuchElementException if no block exists at {@code index}
     */
    @Override
    public Block loadBlock(int index) {
        lock.readLock().lock();
        try {
            Block block = store.get(index);
            if (block == null) {
                throw new NoSuchElementException(
                    "InMemoryStorage: no block found at index " + index);
            }
            return block;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Searches for a block by its SHA-256 hash using a constant-time comparison
     * to prevent hash timing attacks (NFR-SEC-03).
     *
     * @param hash hex-encoded block hash (non-null)
     * @return an {@link Optional} containing the matching block, or empty if absent
     * @throws NullPointerException if {@code hash} is null
     */
    @Override
    public Optional<Block> loadBlockByHash(String hash) {
        Objects.requireNonNull(hash, "hash must not be null");
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
     * Returns all stored blocks in ascending index order.
     *
     * <p>Because the backing store is a {@link TreeMap} keyed by block index,
     * {@link java.util.TreeMap#values()} iterates in ascending key order by definition —
     * no explicit sort is needed even when blocks were saved out of sequence.</p>
     *
     * <p>The returned list is a defensive copy; mutations do not affect the store.</p>
     *
     * @return non-null, index-ordered list (maybe empty)
     */
    @Override
    public List<Block> loadAll() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(store.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns {@code true} if a block with the given hash is present in the store.
     *
     * <p>Uses constant-time comparison to prevent timing side-channel attacks.</p>
     *
     * @param hash hex-encoded block hash (non-null)
     * @return {@code true} if a matching block exists
     * @throws NullPointerException if {@code hash} is null
     */
    @Override
    public boolean exists(String hash) {
        Objects.requireNonNull(hash, "hash must not be null");
        lock.readLock().lock();
        try {
            return store.values().stream()
                .anyMatch(b -> constantTimeHashEquals(b.getHash(), hash));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns the number of blocks currently in the store.
     *
     * @return block count (&ge; 0)
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

    // ─── Internal helpers ─────────────────────────────────────────────────────

    /**
     * Removes all blocks from the in-memory store.
     *
     * <p><strong>Warning:</strong> This operation is irreversible and intended
     * for testing / chain-reset scenarios only.</p>
     */
    @Override
    public void deleteAll() {
        lock.writeLock().lock();
        try {
            int count = store.size();
            store.clear();
            LOGGER.log(Level.INFO, "InMemoryStorage: deleted all {0} blocks", count);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Returns a human-readable description of this storage instance.
     *
     * @return string representation
     */
    @Override
    public String toString() {
        lock.readLock().lock();
        try {
            return "InMemoryStorage{blocks=" + store.size() + '}';
        } finally {
            lock.readLock().unlock();
        }
    }
}
