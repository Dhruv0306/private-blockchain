package com.privatechain.storage.rocksdb;

import com.privatechain.core.exception.BlockValidationException;
import com.privatechain.core.model.Block;
import com.privatechain.core.spi.BlockchainStorage;
import com.privatechain.storage.BlockSerializer;
import org.rocksdb.BloomFilter;
import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.CompressionType;
import org.rocksdb.LRUCache;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * High write-throughput, crash-safe {@link BlockchainStorage} implementation
 * backed by RocksDB.
 *
 * <p>RocksDB is Facebook's LSM-tree storage engine optimized for high-write
 * workloads on SSDs. It provides write-ahead logging (WAL) and crash recovery
 * (NFR-REL-01, FR-STOR-04). Each block is stored under a big-endian 4-byte
 * integer key, serialized to JSON via {@link BlockSerializer}.</p>
 *
 * <h2>Key layout</h2>
 * <pre>
 * Key  : big-endian 4-byte block index (e.g., block 7 → 0x00_00_00_07)
 * Value: UTF-8 JSON of the Block object
 * </pre>
 *
 * <h2>Performance tuning</h2>
 * <p>The default configuration enables:</p>
 * <ul>
 *   <li>Snappy compression to reduce disk footprint</li>
 *   <li>64 MB write buffer for batching sequential writes</li>
 *   <li>Bloom filters on block-based table to speed up point reads</li>
 * </ul>
 *
 * <h2>Thread safety</h2>
 * <p>RocksDB is internally thread-safe for concurrent reads. This class adds an
 * additional {@link ReentrantReadWriteLock} to serialize writes and protect the
 * {@link #close()} method from concurrent access.</p>
 *
 * <h2>Lifecycle</h2>
 * <pre>{@code
 * try (RocksDBStorage storage = new RocksDBStorage("/data/chain-rocks")) {
 *     BlockchainNode node = BlockchainConfig.builder()
 *         .storage(storage)
 *         .build();
 *     node.start();
 * }
 * }</pre>
 *
 * @see BlockchainStorage
 * @see BlockSerializer
 * @since 1.0.0
 */
public final class RocksDBStorage implements BlockchainStorage, AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(RocksDBStorage.class.getName());

    /** Prefix byte used to distinguish block-index keys from future meta-keys. */
    private static final byte KEY_PREFIX_BLOCK = (byte) 0x01;

    /** Number of bytes in the composite key: 1 prefix byte + 4 index bytes. */
    private static final int KEY_LENGTH = 5;

    static {
        // Load the RocksDB native library exactly once per JVM
        RocksDB.loadLibrary();
    }

    /**
     * The underlying RocksDB database handle. Opened once in the constructor;
     * closed in {@link #close()}.
     */
    private final RocksDB db;

    /**
     * Shared write options configured for WAL-backed durability.
     * Reused across writes to avoid per-operation allocation overhead.
     */
    private final WriteOptions writeOptions;

    /**
     * Guards concurrent access. Read lock allows parallel queries;
     * write lock serializes all mutations.
     */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /** Human-readable path used in log messages and {@link #toString()}. */
    private final String dataPath;

    // ─── Constructor ──────────────────────────────────────────────────────────

    /**
     * Opens (or creates) a RocksDB database at the given directory path.
     *
     * <p>The database directory is created automatically if it does not exist.
     * If the database is already locked by another process, the constructor
     * will throw immediately.</p>
     *
     * @param dataDirectory path to the directory where RocksDB files will be stored
     * @throws NullPointerException     if {@code dataDirectory} is null
     * @throws IllegalArgumentException if {@code dataDirectory} is blank
     * @throws BlockValidationException if the database cannot be opened
     */
    public RocksDBStorage(String dataDirectory) {
        Objects.requireNonNull(dataDirectory, "dataDirectory must not be null");
        if (dataDirectory.isBlank()) {
            throw new IllegalArgumentException("dataDirectory must not be blank");
        }
        this.dataPath = dataDirectory;

        // Bloom filter reduces disk I/O for point lookups (false positive rate ~= 1%).
        // LRUCache is the correct RocksDB Java 7+ API; setBlockCacheSize() was removed.
        LRUCache blockCache = new LRUCache(64 * 1024 * 1024L); // 64 MB read cache
        BlockBasedTableConfig tableConfig = new BlockBasedTableConfig()
            .setFilterPolicy(new BloomFilter(10, false))
            .setBlockCache(blockCache);

        Options options = new Options()
            .setCreateIfMissing(true)
            .setCompressionType(CompressionType.SNAPPY_COMPRESSION)
            .setWriteBufferSize(64 * 1024 * 1024L) // 64 MB write buffer
            .setMaxWriteBufferNumber(3)
            .setTableFormatConfig(tableConfig);

        this.writeOptions = new WriteOptions().setSync(false); // WAL provides crash-safety

        try {
            this.db = RocksDB.open(options, dataDirectory);
            LOGGER.log(Level.INFO, "RocksDBStorage opened at {0}", dataDirectory);
        } catch (RocksDBException ex) {
            throw new BlockValidationException(
                "Failed to open RocksDB at path: " + dataDirectory + " — " + ex.getMessage(),
                ex, null);
        }
    }

    // ─── BlockchainStorage implementation ────────────────────────────────────

    /**
     * Persists a block atomically using a RocksDB {@link WriteBatch}.
     *
     * <p>Write batches in RocksDB are atomic: either the entire batch is applied
     * or none of it is, satisfying the atomicity requirement of
     * {@link BlockchainStorage#saveBlock}.</p>
     *
     * @param block the block to persist (non-null)
     * @throws NullPointerException     if {@code block} is null
     * @throws BlockValidationException if the write fails
     */
    @Override
    public void saveBlock(Block block) {
        Objects.requireNonNull(block, "block must not be null");
        byte[] key   = blockKey(block.getIndex());
        byte[] value = BlockSerializer.toBytes(block);

        lock.writeLock().lock();
        try (WriteBatch batch = new WriteBatch()) {
            batch.put(key, value);
            db.write(writeOptions, batch);
            LOGGER.fine(() -> "RocksDB saved block index=" + block.getIndex()
                + " hash=" + block.getHash().substring(0, 16) + "...");
        } catch (RocksDBException ex) {
            throw new BlockValidationException(
                "RocksDB write failed for block at index " + block.getIndex()
                    + ": " + ex.getMessage(),
                ex, null);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Retrieves a block by its zero-based index.
     *
     * @param index block index (&ge; 0)
     * @return the deserialized {@link Block}
     * @throws NoSuchElementException   if no block exists at {@code index}
     * @throws BlockValidationException if deserialization or hash verification fails
     */
    @Override
    public Block loadBlock(int index) {
        byte[] key = blockKey(index);
        lock.readLock().lock();
        try {
            byte[] value = db.get(key);
            if (value == null) {
                throw new NoSuchElementException(
                    "RocksDBStorage: no block found at index " + index);
            }
            return BlockSerializer.fromBytes(value);
        } catch (RocksDBException ex) {
            throw new BlockValidationException(
                "RocksDB read failed for block at index " + index + ": " + ex.getMessage(),
                ex, null);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Searches for a block by its SHA-256 hash.
     *
     * <p>Performs a sequential scan — suitable for moderate chain sizes.
     * For very large chains a secondary hash→index should be maintained.</p>
     *
     * @param hash hex-encoded block hash (non-null)
     * @return an {@link Optional} containing the matching block, or empty if absent
     * @throws NullPointerException if {@code hash} is null
     */
    @Override
    public Optional<Block> loadBlockByHash(String hash) {
        Objects.requireNonNull(hash, "hash must not be null");
        lock.readLock().lock();
        try (RocksIterator iterator = db.newIterator()) {
            iterator.seekToFirst();
            while (iterator.isValid()) {
                byte[] key = iterator.key();
                if (isBlockKey(key)) {
                    Block block = BlockSerializer.fromBytes(iterator.value());
                    if (constantTimeHashEquals(block.getHash(), hash)) {
                        return Optional.of(block);
                    }
                }
                iterator.next();
            }
            return Optional.empty();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns all stored blocks in ascending index order.
     *
     * <p>RocksDB's lexicographic ordering of big-endian keys provides natural
     * ascending order without any post-scan sort.</p>
     *
     * @return non-null, ordered list (maybe empty)
     */
    @Override
    public List<Block> loadAll() {
        lock.readLock().lock();
        try (RocksIterator iterator = db.newIterator()) {
            List<Block> blocks = new ArrayList<>();
            iterator.seekToFirst();
            while (iterator.isValid()) {
                if (isBlockKey(iterator.key())) {
                    blocks.add(BlockSerializer.fromBytes(iterator.value()));
                }
                iterator.next();
            }
            return blocks;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns {@code true} if a block with the given hash is stored.
     *
     * @param hash hex-encoded block hash (non-null)
     * @return {@code true} if present
     * @throws NullPointerException if {@code hash} is null
     */
    @Override
    public boolean exists(String hash) {
        return loadBlockByHash(hash).isPresent();
    }

    /**
     * Returns the number of stored blocks by counting block-keyed entries.
     *
     * @return block count (&ge; 0)
     */
    @Override
    public int chainHeight() {
        lock.readLock().lock();
        try (RocksIterator iterator = db.newIterator()) {
            int count = 0;
            iterator.seekToFirst();
            while (iterator.isValid()) {
                if (isBlockKey(iterator.key())) {
                    count++;
                }
                iterator.next();
            }
            return count;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Removes all block entries from the RocksDB store.
     *
     * <p><strong>Warning:</strong> This operation is irreversible. Intended for
     * testing and chain-reset scenarios only.</p>
     */
    @Override
    public void deleteAll() {
        lock.writeLock().lock();
        try (RocksIterator iterator = db.newIterator()) {
            iterator.seekToFirst();
            int deleted = 0;
            while (iterator.isValid()) {
                byte[] key = iterator.key();
                if (isBlockKey(key)) {
                    db.delete(key);
                    deleted++;
                }
                iterator.next();
            }
            LOGGER.log(Level.INFO, "RocksDBStorage: deleted {0} blocks from {1}",
                new Object[]{deleted, dataPath});
        } catch (RocksDBException ex) {
            throw new BlockValidationException(
                "RocksDB deleteAll failed: " + ex.getMessage(),
                ex, null);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ─── AutoCloseable ────────────────────────────────────────────────────────

    /**
     * Closes the RocksDB database and releases all native resources.
     *
     * <p>Must be called when the owning node shuts down to flush the write buffer
     * and release the file lock. Subsequent calls are idempotent.</p>
     */
    @Override
    public void close() {
        lock.writeLock().lock();
        try {
            writeOptions.close();
            db.close();
            LOGGER.log(Level.INFO, "RocksDBStorage closed at {0}", dataPath);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ─── Key encoding helpers ─────────────────────────────────────────────────

    /**
     * Encodes a block index as a 5-byte composite key:
     * {@code [KEY_PREFIX_BLOCK (1 byte)] + [index as big-endian 4 bytes]}.
     *
     * @param index block index (&ge; 0)
     * @return 5-byte key array
     */
    private static byte[] blockKey(int index) {
        ByteBuffer buf = ByteBuffer.allocate(KEY_LENGTH).order(ByteOrder.BIG_ENDIAN);
        buf.put(KEY_PREFIX_BLOCK);
        buf.putInt(index);
        return buf.array();
    }

    /**
     * Returns {@code true} if the given key matches the block key format.
     *
     * @param key raw key bytes from the iterator
     * @return {@code true} if the key has the block prefix and correct length
     */
    private static boolean isBlockKey(byte[] key) {
        return key != null && key.length == KEY_LENGTH && key[0] == KEY_PREFIX_BLOCK;
    }

    /**
     * Compares two hex-encoded hash strings in constant time (NFR-SEC-03).
     *
     * @param a first hash string
     * @param b second hash string
     * @return {@code true} if the strings represent the same hash value
     */
    private static boolean constantTimeHashEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(
            a.getBytes(StandardCharsets.UTF_8),
            b.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Returns a human-readable description of this storage instance.
     *
     * @return string representation
     */
    @Override
    public String toString() {
        return "RocksDBStorage{path='" + dataPath + "'}";
    }
}
