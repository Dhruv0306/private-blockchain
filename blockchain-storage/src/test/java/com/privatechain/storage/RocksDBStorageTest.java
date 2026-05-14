package com.privatechain.storage;

import com.privatechain.core.builder.GenesisBlockFactory;
import com.privatechain.core.model.Block;
import com.privatechain.core.spi.BlockchainStorage;
import com.privatechain.storage.rocksdb.RocksDBStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the full {@link StorageContractTest} Technology Compatibility Kit (TCK)
 * against {@link RocksDBStorage}, plus implementation-specific tests that cover
 * paths not reachable through the abstract contract tests.
 *
 * <p>{@link RocksDBStorage} provides high write-throughput persistent storage via
 * the RocksDB JNI wrapper (FR-STOR-04, T-037).</p>
 *
 * <h2>Resource management</h2>
 * <p>Every test that opens a {@link RocksDBStorage} directly does so inside a
 * {@code try}-with-resources block. The TCK lifecycle pair
 * ({@link #createStorage()} / {@link #destroyStorage(BlockchainStorage)}) manages
 * the TCK instance by casting the {@link BlockchainStorage} back to
 * {@link RocksDBStorage} in {@code destroyStorage()} and calling
 * {@link RocksDBStorage#close()}.</p>
 *
 * @since 1.0.0
 */
@DisplayName("RocksDBStorage — TCK + unit tests")
class RocksDBStorageTest extends StorageContractTest {

    private static final Logger LOGGER = Logger.getLogger(RocksDBStorageTest.class.getName());

    @TempDir
    Path tempDir;

    // ─── TCK lifecycle ────────────────────────────────────────────────────────

    /**
     * Creates a fresh {@link RocksDBStorage} in a per-test subdirectory.
     *
     * @return a new, empty {@link RocksDBStorage}
     */
    @Override
    protected BlockchainStorage createStorage() {
        Path dbDir = tempDir.resolve("rocksdb-" + System.nanoTime());
        // Opened here; closed in destroyStorage() via AutoCloseable cast.
        return new RocksDBStorage(dbDir.toString());
    }

    /**
     * Closes the RocksDB database handle, flushing the write buffer before JUnit
     * deletes the temp directory.
     *
     * @param storage the storage instance opened by {@link #createStorage()}
     */
    @Override
    protected void destroyStorage(BlockchainStorage storage) {
        if (storage instanceof RocksDBStorage rocksDB) {
            try {
                rocksDB.close();
            } catch (Exception ex) {
                LOGGER.warning("RocksDBStorageTest: failed to close DB: " + ex.getMessage());
            }
        }
    }

    // ─── Implementation-specific tests ───────────────────────────────────────

    /**
     * Tests specific to {@link RocksDBStorage} covering branches not exercised by
     * the generic TCK: constructor guards, {@code toString()}, lifecycle, and
     * the persistence guarantee (AC-04).
     *
     * <p>Every test that opens a {@link RocksDBStorage} directly uses
     * {@code try}-with-resources so the native write buffer is flushed and the
     * file lock is released promptly.</p>
     */
    @Nested
    @DisplayName("RocksDBStorage — implementation-specific")
    class RocksDBSpecificTests {

        @Test
        @DisplayName("constructor throws IllegalArgumentException for blank path")
        void blankPathThrows() {
            // Plain try/catch — the constructor throws before acquiring any native
            // resource, so no AutoCloseable is ever created.
            try {
                new RocksDBStorage("   ").close();
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
                new RocksDBStorage(null).close();
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
        @DisplayName("toString() contains 'RocksDBStorage'")
        void toStringContainsClassName(@TempDir Path dir) {
            try (RocksDBStorage storage = new RocksDBStorage(
                dir.resolve("ts-chain").toString())) {
                assertTrue(storage.toString().contains("RocksDBStorage"),
                    "toString() must identify the implementation class");
            }
        }

        @Test
        @DisplayName("toString() contains the configured directory path")
        void toStringContainsPath(@TempDir Path dir) {
            try (RocksDBStorage storage = new RocksDBStorage(
                dir.resolve("my-rocksdb").toString())) {
                assertTrue(storage.toString().contains("my-rocksdb"),
                    "toString() must include the database path");
            }
        }

        @Test
        @DisplayName("saveBlock() throws NullPointerException for null block")
        void saveNullBlockThrows(@TempDir Path dir) {
            try (RocksDBStorage storage = new RocksDBStorage(
                dir.resolve("null-test").toString())) {
                assertThrows(NullPointerException.class, () -> storage.saveBlock(null));
            }
        }

        @Test
        @DisplayName("loadBlockByHash() throws NullPointerException for null hash")
        void loadBlockByHashNullThrows(@TempDir Path dir) {
            try (RocksDBStorage storage = new RocksDBStorage(
                dir.resolve("null-hash").toString())) {
                assertThrows(NullPointerException.class,
                    () -> storage.loadBlockByHash(null));
            }
        }

        @Test
        @DisplayName("exists() throws NullPointerException for null hash")
        void existsNullThrows(@TempDir Path dir) {
            try (RocksDBStorage storage = new RocksDBStorage(
                dir.resolve("exists-null").toString())) {
                assertThrows(NullPointerException.class, () -> storage.exists(null));
            }
        }

        @Test
        @DisplayName("data persists after close and re-open (crash-safety AC-04)")
        void dataPersistsAfterReopenSameDirectory(@TempDir Path dir) {
            Path dbDir = dir.resolve("persist-test");
            Block genesis = GenesisBlockFactory.create("rocks-persist-chain");

            // First session: write and close.
            try (RocksDBStorage first = new RocksDBStorage(dbDir.toString())) {
                first.saveBlock(genesis);
            } // close() flushes write buffer and releases the file lock

            // Second session: reopen the same directory and verify data is intact.
            try (RocksDBStorage second = new RocksDBStorage(dbDir.toString())) {
                assertTrue(second.exists(genesis.getHash()),
                    "Block must survive RocksDB close and re-open (AC-04)");
                assertTrue(second.loadBlock(0).isHashValid(),
                    "Reloaded block hash must be self-consistent after re-open");
            }
        }
    }
}
