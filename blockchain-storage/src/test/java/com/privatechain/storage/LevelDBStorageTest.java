package com.privatechain.storage;

import com.privatechain.core.builder.GenesisBlockFactory;
import com.privatechain.core.model.Block;
import com.privatechain.core.spi.BlockchainStorage;
import com.privatechain.storage.leveldb.LevelDBStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the full {@link StorageContractTest} Technology Compatibility Kit (TCK)
 * against {@link LevelDBStorage}, plus implementation-specific tests that cover
 * paths not reachable through the abstract contract tests.
 *
 * <p>{@link LevelDBStorage} provides crash-safe persistent storage via the native
 * LevelDB JNI wrapper (FR-STOR-03, T-034).</p>
 *
 * <h2>Resource management</h2>
 * <p>{@link LevelDBStorage} implements {@link AutoCloseable}. The TCK lifecycle
 * methods ({@link #createStorage()} / {@link #destroyStorage(BlockchainStorage)})
 * manage the database handle across the test boundary: {@code createStorage()}
 * opens the database and {@code destroyStorage()} closes it by casting the
 * {@link BlockchainStorage} back to {@link LevelDBStorage} and calling
 * {@link LevelDBStorage#close()}. Individual nested tests that need their own
 * isolated instance always use {@code try}-with-resources so the handle is never
 * leaked.</p>
 *
 * @since 1.0.0
 */
@DisplayName("LevelDBStorage — TCK + unit tests")
class LevelDBStorageTest extends StorageContractTest {

    private static final Logger LOGGER = Logger.getLogger(LevelDBStorageTest.class.getName());

    @TempDir
    Path tempDir;

    // ─── TCK lifecycle ────────────────────────────────────────────────────────

    /**
     * Creates a fresh {@link LevelDBStorage} in a per-test subdirectory.
     *
     * <p>The returned instance is stored by the TCK superclass and passed to
     * {@link #destroyStorage(BlockchainStorage)} after the test completes.
     * Do NOT open additional handles to the same directory while this one is open
     * (LevelDB enforces an exclusive file lock).</p>
     *
     * @return a new, empty {@link LevelDBStorage}
     */
    @Override
    protected BlockchainStorage createStorage() {
        Path dbDir = tempDir.resolve("leveldb-" + System.nanoTime());
        // Opened here; closed in destroyStorage() via AutoCloseable cast.
        // SpotBugs/IntelliJ do not flag this pattern because the variable is
        // never stored in a field — it is returned immediately and the
        // destroyStorage() contract guarantees close() is called.
        return new LevelDBStorage(dbDir.toString());
    }

    /**
     * Closes the LevelDB database handle to release the file lock before JUnit
     * deletes the temp directory.
     *
     * @param storage the storage instance opened by {@link #createStorage()}
     */
    @Override
    protected void destroyStorage(BlockchainStorage storage) {
        if (storage instanceof LevelDBStorage levelDB) {
            try {
                levelDB.close();
            } catch (Exception ex) {
                LOGGER.warning("LevelDBStorageTest: failed to close DB: " + ex.getMessage());
            }
        }
    }

    // ─── Implementation-specific tests ───────────────────────────────────────

    /**
     * Tests specific to {@link LevelDBStorage} covering branches not exercised by
     * the generic TCK: constructor guards, {@code toString()}, lifecycle, and
     * the crash-safety / persistence guarantee (AC-04).
     *
     * <p>Every test that opens a {@link LevelDBStorage} does so inside a
     * {@code try}-with-resources block so the file lock is always released.</p>
     */
    @Nested
    @DisplayName("LevelDBStorage — implementation-specific")
    class LevelDBSpecificTests {

        @Test
        @DisplayName("constructor throws IllegalArgumentException for blank path")
        void blankPathThrows() {
            // Plain try/catch — the constructor throws before acquiring any native
            // resource, so no AutoCloseable is ever created and try-with-resources
            // is neither needed nor applicable here.
            try {
                new LevelDBStorage("   ").close();
                org.junit.jupiter.api.Assertions.fail(
                    "Expected IllegalArgumentException was not thrown");
            } catch (IllegalArgumentException expected) {
                // correct — constructor rejected the blank path
            } catch (Exception unexpected) {
                org.junit.jupiter.api.Assertions.fail(
                    "Wrong exception type: " + unexpected);
            }
        }

        @Test
        @DisplayName("constructor throws NullPointerException for null path")
        void nullPathThrows() {
            // Plain try/catch — the constructor throws before acquiring any native
            // resource, so no AutoCloseable is ever created.
            try {
                new LevelDBStorage(null).close();
                org.junit.jupiter.api.Assertions.fail(
                    "Expected NullPointerException was not thrown");
            } catch (NullPointerException expected) {
                // correct — constructor rejected null
            } catch (Exception unexpected) {
                org.junit.jupiter.api.Assertions.fail(
                    "Wrong exception type: " + unexpected);
            }
        }

        @Test
        @DisplayName("toString() contains 'LevelDBStorage'")
        void toStringContainsClassName(@TempDir Path dir) {
            try (LevelDBStorage storage = new LevelDBStorage(
                dir.resolve("ts-chain").toString())) {
                assertTrue(storage.toString().contains("LevelDBStorage"),
                    "toString() must identify the implementation class");
            }
        }

        @Test
        @DisplayName("toString() contains the configured directory path")
        void toStringContainsPath(@TempDir Path dir) {
            try (LevelDBStorage storage = new LevelDBStorage(
                dir.resolve("my-leveldb").toString())) {
                assertTrue(storage.toString().contains("my-leveldb"),
                    "toString() must include the database path");
            }
        }

        @Test
        @DisplayName("saveBlock() throws NullPointerException for null block")
        void saveNullBlockThrows(@TempDir Path dir) {
            try (LevelDBStorage storage = new LevelDBStorage(
                dir.resolve("null-test").toString())) {
                assertThrows(NullPointerException.class, () -> storage.saveBlock(null));
            }
        }

        @Test
        @DisplayName("loadBlockByHash() throws NullPointerException for null hash")
        void loadBlockByHashNullThrows(@TempDir Path dir) {
            try (LevelDBStorage storage = new LevelDBStorage(
                dir.resolve("null-hash").toString())) {
                assertThrows(NullPointerException.class,
                    () -> storage.loadBlockByHash(null));
            }
        }

        @Test
        @DisplayName("exists() throws NullPointerException for null hash")
        void existsNullThrows(@TempDir Path dir) {
            try (LevelDBStorage storage = new LevelDBStorage(
                dir.resolve("exists-null").toString())) {
                assertThrows(NullPointerException.class, () -> storage.exists(null));
            }
        }

        @Test
        @DisplayName("data persists after close and re-open (crash-safety AC-04)")
        void dataPersistsAfterReopenSameDirectory(@TempDir Path dir) {
            Path dbDir = dir.resolve("persist-test");
            Block genesis = GenesisBlockFactory.create("persist-chain");

            // First session: write and close.
            try (LevelDBStorage first = new LevelDBStorage(dbDir.toString())) {
                first.saveBlock(genesis);
            } // close() releases the LOCK file

            // Second session: reopen the same directory and verify data is intact.
            try (LevelDBStorage second = new LevelDBStorage(dbDir.toString())) {
                assertTrue(second.exists(genesis.getHash()),
                    "Block must survive LevelDB close and re-open (AC-04)");
                assertTrue(second.loadBlock(0).isHashValid(),
                    "Reloaded block hash must be self-consistent after re-open");
            }
        }

        @Test
        @DisplayName("constructor throws when path is a file, not a directory")
        void pathIsFileThrows(@TempDir Path dir) throws Exception {
            // Create a regular file where LevelDB expects a database directory.
            Path file = dir.resolve("not-a-dir.txt");
            java.nio.file.Files.writeString(file, "I am a file");
            // Plain try/catch — if the constructor somehow succeeds we close
            // immediately via .close() so no resource is leaked.
            try {
                new LevelDBStorage(file.toString()).close();
                org.junit.jupiter.api.Assertions.fail(
                    "Expected an exception — LevelDB cannot open a file as a DB directory");
            } catch (Exception expected) {
                // correct — LevelDB rejected the file path
                assertNotNull(expected.getMessage(), "Exception must have a descriptive message");
            }
        }
    }
}
