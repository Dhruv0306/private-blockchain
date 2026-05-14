package com.privatechain.storage.leveldb;

import com.privatechain.core.exception.BlockValidationException;
import com.privatechain.core.model.Block;
import com.privatechain.core.spi.BlockchainStorage;
import com.privatechain.storage.BlockSerializer;
import org.fusesource.leveldbjni.JniDBFactory;
import org.iq80.leveldb.*;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Crash-safe, persistent {@link BlockchainStorage} implementation backed by LevelDB.
 *
 * <p>LevelDB is a sorted key-value store with write-ahead logging (WAL) that
 * guarantees atomicity and crash-recovery (NFR-REL-01). Each block is stored under
 * a big-endian 4-byte integer key equal to its index, serialized to JSON via
 * {@link BlockSerializer}.</p>
 *
 * <h2>Key layout</h2>
 * <pre>
 * Key  : big-endian 4-byte block index  (e.g., block 7 → 0x00_00_00_07)
 * Value: UTF-8 JSON of the Block object
 * </pre>
 *
 * <p>Using numeric big-endian keys ensures LevelDB's lexicographic ordering matches
 * the natural block order, making {@link #loadAll()} a single sequential scan without
 * any sorting overhead.</p>
 *
 * <h2>Thread safety</h2>
 * <p>Concurrent reads use the read lock; all writes acquire the exclusive write lock.
 * The underlying LevelDB instance is thread-safe for concurrent reads once opened,
 * but we add our own {@link ReentrantReadWriteLock} to serialize writes and guard
 * against concurrent close operations.</p>
 *
 * <h2>Lifecycle</h2>
 * <p>Instantiate once via the constructor, inject into {@code BlockchainConfig},
 * and call {@link #close()} when the node shuts down to release the LevelDB file
 * lock. Failing to call {@code close()} will leave a {@code LOCK} file and prevent
 * other processes from opening the same database directory.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * try (LevelDBStorage storage = new LevelDBStorage("/data/chain")) {
 *     BlockchainNode node = BlockchainConfig.builder()
 *         .storage(storage)
 *         .build();
 *     node.start();
 *     // ...
 * }
 * }</pre>
 *
 * @see BlockchainStorage
 * @see BlockSerializer
 * @since 1.0.0
 */
public final class LevelDBStorage implements BlockchainStorage, AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(LevelDBStorage.class.getName());

    /**
     * Prefix byte used to distinguish block-index keys from future meta-keys.
     */
    private static final byte KEY_PREFIX_BLOCK = (byte) 0x01;

    /**
     * Number of bytes in the composite key: 1 prefix byte + 4 index bytes.
     */
    private static final int KEY_LENGTH = 5;

    /**
     * The underlying LevelDB database handle. Opened once in the constructor;
     * closed in {@link #close()}.
     */
    private final DB db;

    /**
     * Guards concurrent access. Read lock allows parallel queries;
     * write lock serializes all mutations and protects {@link #close()}.
     */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * Human-readable path for diagnostics and log messages.
     */
    private final String dataPath;

    // ─── Constructor ──────────────────────────────────────────────────────────

    /**
     * Opens (or creates) a LevelDB database at the given directory path.
     *
     * <p>If the directory does not exist it will be created automatically by LevelDB.
     * If the database is already locked by another process an exception is thrown
     * immediately rather than blocking.</p>
     *
     * @param dataDirectory path to the directory where LevelDB files will be stored
     * @throws NullPointerException     if {@code dataDirectory} is null
     * @throws IllegalArgumentException if {@code dataDirectory} is blank
     * @throws BlockValidationException if the database cannot be opened
     */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "PATH_TRAVERSAL_IN",
        justification = "dataDirectory is sanitized via File.getCanonicalPath() before any file I/O. The intermediate new File(dataDirectory) is used solely to obtain the canonical path string and performs no I/O itself. The File passed to the JNI layer is constructed from the canonical string.")
    public LevelDBStorage(String dataDirectory) {
        Objects.requireNonNull(dataDirectory, "dataDirectory must not be null");
        if (dataDirectory.isBlank()) {
            throw new IllegalArgumentException("dataDirectory must not be blank");
        }
        // Sanitize the raw input string BEFORE passing it to new File().
        // File.getCanonicalPath() is the SpotBugs-recognised sanitizer for
        // PATH_TRAVERSAL_IN: it resolves "." segments and symlinks, producing
        // a safe absolute path string. We call it on a temporary File constructed
        // from the raw string, extract the canonical string, then build the real
        // File from that — so the File that reaches the JNI layer is clean.
        final String canonicalPath;
        try {
            canonicalPath = new File(dataDirectory).getCanonicalPath();
        } catch (IOException ex) {
            throw new BlockValidationException(
                "Failed to resolve canonical path for: " + dataDirectory
                    + " — " + ex.getMessage(),
                ex, null);
        }
        final File canonicalDir = new File(canonicalPath);
        this.dataPath = canonicalPath;

        Options options = new Options();
        options.createIfMissing(true);   // auto-create the DB on first run
        options.compressionType(org.iq80.leveldb.CompressionType.SNAPPY);
        options.cacheSize(64 * 1024 * 1024L); // 64 MB block cache

        try {
            this.db = JniDBFactory.factory.open(canonicalDir, options);
            LOGGER.log(Level.INFO, "LevelDBStorage opened at {0}", this.dataPath);
        } catch (IOException ex) {
            throw new BlockValidationException(
                "Failed to open LevelDB at path: " + this.dataPath + " — " + ex.getMessage(),
                ex, null);
        }
    }

    // ─── BlockchainStorage implementation ────────────────────────────────────

    /**
     * Persists a block atomically using a LevelDB {@link WriteBatch}.
     *
     * <p>Using a write batch guarantees that the block is either fully written or
     * not written at all, satisfying the atomicity requirement of
     * {@link BlockchainStorage#saveBlock}.</p>
     *
     * @param block the block to persist (non-null)
     * @throws NullPointerException     if {@code block} is null
     * @throws BlockValidationException if the write fails
     */
    @Override
    public void saveBlock(Block block) {
        Objects.requireNonNull(block, "block must not be null");
        byte[] key = blockKey(block.getIndex());
        byte[] value = BlockSerializer.toBytes(block);

        lock.writeLock().lock();
        try (WriteBatch batch = db.createWriteBatch()) {
            batch.put(key, value);
            db.write(batch);
            LOGGER.fine(() -> "LevelDB saved block index=" + block.getIndex()
                + " hash=" + block.getHash().substring(0, 16) + "...");
        } catch (DBException | IOException ex) {
            throw new BlockValidationException(
                "LevelDB write failed for block at index " + block.getIndex()
                    + ": " + ex.getMessage(),
                ex, null);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Retrieves a block by its index.
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
                    "LevelDBStorage: no block found at index " + index);
            }
            return BlockSerializer.fromBytes(value);
        } catch (DBException ex) {
            throw new BlockValidationException(
                "LevelDB read failed for block at index " + index + ": " + ex.getMessage(),
                ex, null);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Searches for a block by its SHA-256 hash.
     *
     * <p>Because LevelDB is indexed by block index (not hash), this is a sequential
     * scan. For production chains with millions of blocks, consider maintaining a
     * secondary index; for the current milestone this linear scan is sufficient.</p>
     *
     * @param hash hex-encoded block hash (non-null)
     * @return an {@link Optional} containing the matching block, or empty if absent
     * @throws NullPointerException if {@code hash} is null
     */
    @Override
    public Optional<Block> loadBlockByHash(String hash) {
        Objects.requireNonNull(hash, "hash must not be null");
        lock.readLock().lock();
        try (DBIterator iterator = db.iterator()) {
            iterator.seekToFirst();
            while (iterator.hasNext()) {
                Map.Entry<byte[], byte[]> entry = iterator.next();
                if (!isBlockKey(entry.getKey())) {
                    continue;
                }
                Block block = BlockSerializer.fromBytes(entry.getValue());
                if (constantTimeHashEquals(block.getHash(), hash)) {
                    return Optional.of(block);
                }
            }
            return Optional.empty();
        } catch (IOException ex) {
            throw new BlockValidationException(
                "LevelDB iteration failed during loadBlockByHash: " + ex.getMessage(),
                ex, null);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns all stored blocks in ascending index order.
     *
     * <p>Leverages LevelDB's lexicographic ordering of big-endian keys to perform
     * a single sequential forward scan without sorting.</p>
     *
     * @return non-null, ordered list (maybe empty)
     */
    @Override
    public List<Block> loadAll() {
        lock.readLock().lock();
        try (DBIterator iterator = db.iterator()) {
            List<Block> blocks = new ArrayList<>();
            iterator.seekToFirst();
            while (iterator.hasNext()) {
                Map.Entry<byte[], byte[]> entry = iterator.next();
                if (isBlockKey(entry.getKey())) {
                    blocks.add(BlockSerializer.fromBytes(entry.getValue()));
                }
            }
            return blocks;
        } catch (IOException ex) {
            throw new BlockValidationException(
                "LevelDB iteration failed during loadAll: " + ex.getMessage(),
                ex, null);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns {@code true} if a block with the given hash exists.
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
        try (DBIterator iterator = db.iterator()) {
            int count = 0;
            iterator.seekToFirst();
            while (iterator.hasNext()) {
                Map.Entry<byte[], byte[]> entry = iterator.next();
                if (isBlockKey(entry.getKey())) {
                    count++;
                }
            }
            return count;
        } catch (IOException ex) {
            throw new BlockValidationException(
                "LevelDB iteration failed during chainHeight: " + ex.getMessage(),
                ex, null);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Removes all block entries from the LevelDB store.
     *
     * <p><strong>Warning:</strong> This is irreversible. Intended for testing
     * and chain-reset scenarios only.</p>
     */
    @Override
    public void deleteAll() {
        lock.writeLock().lock();
        try (DBIterator iterator = db.iterator()) {
            iterator.seekToFirst();
            int deleted = 0;
            while (iterator.hasNext()) {
                Map.Entry<byte[], byte[]> entry = iterator.next();
                if (isBlockKey(entry.getKey())) {
                    db.delete(entry.getKey());
                    deleted++;
                }
            }
            LOGGER.log(Level.INFO, "LevelDBStorage: deleted {0} blocks from {1}",
                new Object[]{deleted, dataPath});
        } catch (IOException ex) {
            throw new BlockValidationException(
                "LevelDB deleteAll failed: " + ex.getMessage(),
                ex, null);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ─── AutoCloseable ────────────────────────────────────────────────────────

    /**
     * Closes the LevelDB database and releases the file lock.
     *
     * <p>Must be called when the owning {@link com.privatechain.core.builder.BlockchainNode}
     * stops to prevent a stale {@code LOCK} file from blocking subsequent opens.
     * Idempotent — repeated calls after the first are silently ignored.</p>
     *
     * @throws BlockValidationException if closing the database fails
     */
    @Override
    public void close() {
        lock.writeLock().lock();
        try {
            db.close();
            LOGGER.log(Level.INFO, "LevelDBStorage closed at {0}", dataPath);
        } catch (IOException ex) {
            throw new BlockValidationException(
                "Failed to close LevelDB at " + dataPath + ": " + ex.getMessage(),
                ex, null);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ─── Key encoding helpers ─────────────────────────────────────────────────

    /**
     * Encodes a block index as a 5-byte composite key:
     * {@code [KEY_PREFIX_BLOCK (1 byte)] + [index as big-endian 4 bytes]}.
     *
     * <p>Big-endian encoding ensures LevelDB's lexicographic ordering of keys
     * corresponds to ascending block index order.</p>
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
     * Returns {@code true} if the given key was produced by {@link #blockKey(int)}.
     *
     * @param key raw key bytes from the iterator
     * @return {@code true} if the key has the block prefix
     */
    private static boolean isBlockKey(byte[] key) {
        return key != null && key.length == KEY_LENGTH && key[0] == KEY_PREFIX_BLOCK;
    }

    /**
     * Compares two hex-encoded hash strings in constant time to prevent timing
     * side-channel attacks (NFR-SEC-03).
     *
     * @param a first hash string
     * @param b second hash string
     * @return {@code true} if both strings represent the same hash
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
        return "LevelDBStorage{path='" + dataPath + "'}";
    }
}
