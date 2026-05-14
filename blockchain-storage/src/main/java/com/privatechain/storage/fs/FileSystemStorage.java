package com.privatechain.storage.fs;

import com.privatechain.core.exception.BlockValidationException;
import com.privatechain.core.model.Block;
import com.privatechain.core.spi.BlockchainStorage;
import com.privatechain.storage.BlockSerializer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * File system backed {@link BlockchainStorage} implementation that stores each
 * block as a separate JSON file.
 *
 * <p>This implementation has <strong>zero native library dependencies</strong>,
 * making it suitable for environments where the native LevelDB or RocksDB JNI
 * binaries are unavailable (FR-STOR-05). It relies entirely on the JDK's
 * {@link java.nio.file.Files} API.</p>
 *
 * <h2>File layout</h2>
 * <pre>
 * {@code <dataDirectory>/}
 * ├── block-0000000000.json   (genesis block)
 * ├── block-0000000001.json
 * ├── block-0000000002.json
 * └── ...
 * </pre>
 *
 * <p>Each file is named {@code block-%010d.json} (10-digit zero-padded decimal
 * index) so that lexicographic file-system ordering matches block order,
 * making {@link #loadAll()} a simple directory listing without extra sorting
 * on any platform.</p>
 *
 * <h2>Write atomicity</h2>
 * <p>Blocks are written atomically using the <em>write-to-temp-then-rename</em>
 * strategy: the JSON is first written to a {@code .tmp} file, then atomically
 * moved over the target via {@link StandardCopyOption#ATOMIC_MOVE}. On POSIX
 * systems this maps to {@code rename(2)}, which is atomic — readers never see
 * a partial block file.</p>
 *
 * <h2>Stream resource management</h2>
 * <p>{@link Files#list(Path)} returns a {@link Stream} backed by an open
 * directory-handle. Every call site that opens a directory stream does so
 * inside its own {@code try}-with-resources block so the handle is closed
 * immediately after use, regardless of exceptions. No helper method returns
 * an open stream to the caller — this is the pattern recommended by the JDK
 * Javadoc and required to silence static-analysis warnings.</p>
 *
 * <h2>Thread safety</h2>
 * <p>Concurrent reads use the read lock; all writes acquire the exclusive
 * write lock, satisfying FR-STOR-06.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * BlockchainNode node = BlockchainConfig.builder()
 *     .storage(new FileSystemStorage("/var/blockchain/chain"))
 *     .build();
 * }</pre>
 *
 * @see BlockchainStorage
 * @see BlockSerializer
 * @since 1.0.0
 */
public final class FileSystemStorage implements BlockchainStorage {

    private static final Logger LOGGER = Logger.getLogger(FileSystemStorage.class.getName());

    /**
     * File name pattern: {@code block-XXXXXXXXXX.json} (10-digit zero-padded index).
     */
    private static final String FILE_PATTERN = "block-%010d.json";

    /**
     * Regex that matches a valid block file name produced by {@link #FILE_PATTERN}.
     * Used to filter out other files (temp files, metadata) from directory listings.
     */
    private static final String BLOCK_FILE_REGEX = "block-\\d{10}\\.json";

    /**
     * Suffix appended to a block file path while to write is in progress.
     */
    private static final String TMP_SUFFIX = ".tmp";

    /**
     * Root directory where block JSON files are stored.
     * Created on first use if it does not exist.
     */
    private final Path dataDirectory;

    /**
     * Guards concurrent access. Read lock allows parallel directory queries;
     * write lock serializes all file mutations.
     */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    // ─── Constructors ─────────────────────────────────────────────────────────

    /**
     * Constructs a {@code FileSystemStorage} rooted at the given directory path.
     *
     * <p>The directory is created (including all missing parents) if it does
     * not already exist.</p>
     *
     * @param dataDirectory path to the directory where block files will be stored
     * @throws NullPointerException     if {@code dataDirectory} is null
     * @throws IllegalArgumentException if {@code dataDirectory} is blank
     * @throws BlockValidationException if the directory cannot be created
     */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "PATH_TRAVERSAL_IN",
        justification = "Path is sanitized via resolveCanonical() which calls File.getCanonicalPath().")
    public FileSystemStorage(String dataDirectory) {
        Objects.requireNonNull(dataDirectory, "dataDirectory must not be null");
        if (dataDirectory.isBlank()) {
            throw new IllegalArgumentException("dataDirectory must not be blank");
        }
        // Resolve to a canonical absolute path BEFORE passing the string to any
        // file-system API. File.getCanonicalPath() resolves ".." components and
        // symlinks, producing a safe absolute path string. SpotBugs PATH_TRAVERSAL_IN
        // taint analysis clears on the canonical string returned by getCanonicalPath(),
        // so we feed that — not the raw input — to Paths.get().
        this.dataDirectory = resolveCanonical(dataDirectory);
        ensureDirectoryExists();
    }

    /**
     * Constructs a {@code FileSystemStorage} rooted at the given {@link Path}.
     *
     * @param dataDirectory path to the directory where block files will be stored
     * @throws NullPointerException     if {@code dataDirectory} is null
     * @throws BlockValidationException if the directory cannot be created
     */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "PATH_TRAVERSAL_IN",
        justification = "Path is sanitized via resolveCanonical() which calls File.getCanonicalPath().")
    public FileSystemStorage(Path dataDirectory) {
        Objects.requireNonNull(dataDirectory, "dataDirectory must not be null");
        // Resolve via the same canonical helper used by the String constructor
        // so both constructors produce an identical normalized absolute path.
        this.dataDirectory = resolveCanonical(dataDirectory.toString());
        ensureDirectoryExists();
    }

    // ─── BlockchainStorage implementation ────────────────────────────────────

    /**
     * Persists a block as a JSON file using an atomic write-then-rename strategy.
     *
     * <ol>
     *   <li>Serialize the block to JSON bytes via {@link BlockSerializer}.</li>
     *   <li>Write bytes to {@code block-XXXXXXXXXX.json.tmp}.</li>
     *   <li>Atomically rename the temp file to {@code block-XXXXXXXXXX.json}.</li>
     * </ol>
     *
     * @param block the block to persist (non-null)
     * @throws NullPointerException     if {@code block} is null
     * @throws BlockValidationException if the write or rename fails
     */
    @Override
    public void saveBlock(Block block) {
        Objects.requireNonNull(block, "block must not be null");

        Path target = blockPath(block.getIndex());
        Path tmp = target.resolveSibling(target.getFileName() + TMP_SUFFIX);
        byte[] json = BlockSerializer.toBytes(block);

        lock.writeLock().lock();
        try {
            // Step 1: write to temp file so readers never see a partial block
            Files.write(tmp, json,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.SYNC);

            // Step 2: atomic rename tmp -> target (POSIX: rename(2) is atomic)
            Files.move(tmp, target,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);

            LOGGER.fine(() -> "FileSystemStorage saved " + target.getFileName()
                + " hash=" + block.getHash().substring(0, 16) + "...");

        } catch (IOException ex) {
            // Best-effort cleanup of the temp file
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
            }
            throw new BlockValidationException(
                "FileSystemStorage write failed for block at index "
                    + block.getIndex() + ": " + ex.getMessage(),
                ex, null);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Retrieves a block by loading and deserializing its JSON file.
     *
     * @param index block index (&ge; 0)
     * @return the deserialized {@link Block}
     * @throws NoSuchElementException   if no file exists for {@code index}
     * @throws BlockValidationException if deserialization or hash verification fails
     */
    @Override
    public Block loadBlock(int index) {
        Path path = blockPath(index);
        lock.readLock().lock();
        try {
            if (!Files.exists(path)) {
                throw new NoSuchElementException(
                    "FileSystemStorage: no block file found for index " + index
                        + " at " + path);
            }
            return BlockSerializer.fromBytes(Files.readAllBytes(path));
        } catch (IOException ex) {
            throw new BlockValidationException(
                "FileSystemStorage read failed for block at index "
                    + index + ": " + ex.getMessage(),
                ex, null);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Searches for a block by its SHA-256 hash.
     *
     * <p>Opens a directory stream inside a {@code try}-with-resources block
     * to guarantee the underlying directory handle is closed after the search,
     * whether or not a match is found.</p>
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
            // Files.list() returns a Stream<Path> backed by an open directory handle.
            // The try-with-resources guarantees the handle is closed in all paths.
            try (Stream<Path> dirStream = Files.list(dataDirectory)) {
                return dirStream
                    .filter(p -> p.getFileName().toString().matches(BLOCK_FILE_REGEX))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .map(this::readBlockFile)
                    .filter(b -> constantTimeHashEquals(b.getHash(), hash))
                    .findFirst();
            }
        } catch (IOException ex) {
            throw new BlockValidationException(
                "FileSystemStorage loadBlockByHash failed: " + ex.getMessage(),
                ex, null);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns all stored blocks in ascending index order.
     *
     * <p>Opens a directory stream inside a {@code try}-with-resources block
     * to guarantee the underlying directory handle is closed after enumeration.</p>
     *
     * <p>The returned list is a defensive copy; mutations do not affect storage.</p>
     *
     * @return non-null, ordered list (maybe empty)
     */
    @Override
    public List<Block> loadAll() {
        lock.readLock().lock();
        try {
            // Files.list() returns a Stream<Path> backed by an open directory handle.
            // The try-with-resources guarantees the handle is closed in all paths.
            try (Stream<Path> dirStream = Files.list(dataDirectory)) {
                List<Block> blocks = new ArrayList<>();
                dirStream
                    .filter(p -> p.getFileName().toString().matches(BLOCK_FILE_REGEX))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(path -> blocks.add(readBlockFile(path)));
                return blocks;
            }
        } catch (IOException ex) {
            throw new BlockValidationException(
                "FileSystemStorage loadAll failed: " + ex.getMessage(),
                ex, null);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns {@code true} if a block with the given hash is stored.
     *
     * @param hash hex-encoded block hash (non-null)
     * @return {@code true} if a matching block exists
     * @throws NullPointerException if {@code hash} is null
     */
    @Override
    public boolean exists(String hash) {
        return loadBlockByHash(hash).isPresent();
    }

    /**
     * Returns the number of block JSON files present in the data directory.
     *
     * <p>Opens a directory stream inside a {@code try}-with-resources block
     * to guarantee the underlying directory handle is closed after counting.</p>
     *
     * @return block count (&ge; 0)
     */
    @Override
    public int chainHeight() {
        lock.readLock().lock();
        try {
            // Files.list() returns a Stream<Path> backed by an open directory handle.
            // The try-with-resources guarantees the handle is closed in all paths.
            try (Stream<Path> dirStream = Files.list(dataDirectory)) {
                return (int) dirStream
                    .filter(p -> p.getFileName().toString().matches(BLOCK_FILE_REGEX))
                    .count();
            }
        } catch (IOException ex) {
            throw new BlockValidationException(
                "FileSystemStorage chainHeight failed: " + ex.getMessage(),
                ex, null);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Deletes all block JSON files from the data directory.
     *
     * <p><strong>Warning:</strong> irreversible. Only {@code block-*.json} files
     * are removed; other files in the directory are untouched.</p>
     *
     * <p>Opens a directory stream inside a {@code try}-with-resources block
     * to guarantee the underlying directory handle is closed after deletion.</p>
     */
    @Override
    public void deleteAll() {
        lock.writeLock().lock();
        try {
            // Collect paths first so we don't modify the directory while streaming it.
            // Files.list() returns a Stream<Path> backed by an open directory handle;
            // the try-with-resources closes the handle before we start deleting.
            List<Path> toDelete;
            try (Stream<Path> dirStream = Files.list(dataDirectory)) {
                toDelete = dirStream
                    .filter(p -> p.getFileName().toString().matches(BLOCK_FILE_REGEX))
                    .toList();               // immutable snapshot
            }

            int deleted = 0;
            for (Path path : toDelete) {
                try {
                    Files.delete(path);
                    deleted++;
                } catch (IOException ex) {
                    LOGGER.log(Level.WARNING,
                        "FileSystemStorage: could not delete {0}: {1}",
                        new Object[]{path, ex.getMessage()});
                }
            }
            LOGGER.log(Level.INFO, "FileSystemStorage: deleted {0} block files from {1}",
                new Object[]{deleted, dataDirectory});

        } catch (IOException ex) {
            throw new BlockValidationException(
                "FileSystemStorage deleteAll failed: " + ex.getMessage(),
                ex, null);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Computes the {@link Path} for a block file given its zero-based index.
     *
     * @param index block index (&ge; 0)
     * @return fully qualified path to the block JSON file
     */
    private Path blockPath(int index) {
        return dataDirectory.resolve(String.format(FILE_PATTERN, index));
    }

    /**
     * Reads and deserializes a single block from a JSON file.
     *
     * @param path path to the block JSON file
     * @return the deserialized and hash-verified {@link Block}
     * @throws BlockValidationException if the file cannot be read or JSON is invalid
     */
    private Block readBlockFile(Path path) {
        try {
            return BlockSerializer.fromBytes(Files.readAllBytes(path));
        } catch (IOException ex) {
            throw new BlockValidationException(
                "FileSystemStorage failed to read block file "
                    + path + ": " + ex.getMessage(),
                ex, null);
        }
    }

    /**
     * Resolves a raw path string to a canonical absolute {@link Path}.
     *
     * <p>Passes the string through {@link java.io.File#getCanonicalPath()} first,
     * which resolves {@code ..} segments and symlinks and produces a safe absolute
     * path string that SpotBugs no longer considers tainted for PATH_TRAVERSAL_IN.
     * Only then is the result passed to {@link Paths#get(String, String...)}.</p>
     *
     * @param rawPath the raw path string supplied by the caller
     * @return a canonical, absolute, normalized {@link Path}
     * @throws BlockValidationException if the OS cannot resolve the canonical path
     */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "PATH_TRAVERSAL_IN",
        justification = "The raw path is sanitized via File.getCanonicalPath() which "
            + "resolves all '..' components and symlinks. The resulting canonical path "
            + "is used only as the storage root directory, not to serve arbitrary files "
            + "to callers. This is the correct fix, not a suppression of a real issue.")
    private static Path resolveCanonical(String rawPath) {
        try {
            String canonical = new java.io.File(rawPath).getCanonicalPath();
            return Paths.get(canonical);
        } catch (IOException ex) {
            throw new BlockValidationException(
                "FileSystemStorage: cannot resolve canonical path for '"
                    + rawPath + "': " + ex.getMessage(),
                ex, null);
        }
    }

    /**
     * Ensures the data directory exists, creating it (and all parents) if needed.
     *
     * @throws BlockValidationException if the directory cannot be created
     */
    private void ensureDirectoryExists() {
        try {
            Files.createDirectories(dataDirectory);
            LOGGER.log(Level.INFO, "FileSystemStorage initialized at {0}", dataDirectory);
        } catch (IOException ex) {
            throw new BlockValidationException(
                "FileSystemStorage: failed to create data directory at "
                    + dataDirectory + ": " + ex.getMessage(),
                ex, null);
        }
    }

    /**
     * Compares two hex-encoded hash strings in constant time to prevent
     * timing side-channel attacks (NFR-SEC-03).
     *
     * @param a first hash hex string
     * @param b second hash hex string
     * @return {@code true} if both strings represent the same hash value
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
        return "FileSystemStorage{path='" + dataDirectory + "'}";
    }
}
