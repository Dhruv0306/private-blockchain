package com.privatechain.storage;

import com.privatechain.core.builder.GenesisBlockFactory;
import com.privatechain.core.exception.BlockValidationException;
import com.privatechain.core.model.Block;
import com.privatechain.core.spi.BlockchainStorage;
import com.privatechain.storage.fs.FileSystemStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the full {@link StorageContractTest} Technology Compatibility Kit (TCK)
 * against {@link FileSystemStorage}, plus implementation-specific tests that cover
 * paths not reachable through the abstract contract tests.
 *
 * <p>{@link FileSystemStorage} stores one JSON file per block, requires zero native
 * libraries, and uses an atomic write-then-rename strategy for crash safety
 * (FR-STOR-05, T-035).</p>
 *
 * @since 1.0.0
 */
@DisplayName("FileSystemStorage — TCK + unit tests")
class FileSystemStorageTest extends StorageContractTest {

    private static final Logger LOGGER = Logger.getLogger(FileSystemStorageTest.class.getName());

    @TempDir
    Path tempDir;

    /**
     * Tracks the per-test subdirectory so {@link #destroyStorage} can clean up.
     */
    private Path testDir;

    /**
     * Creates a fresh {@link FileSystemStorage} in a per-test subdirectory.
     *
     * @return a new, empty {@link FileSystemStorage}
     */
    @Override
    protected BlockchainStorage createStorage() {
        testDir = tempDir.resolve("chain-" + System.nanoTime());
        return new FileSystemStorage(testDir);
    }

    /**
     * Deletes all block files and the per-test directory.
     *
     * @param storage the storage instance to tear down
     */
    @Override
    protected void destroyStorage(BlockchainStorage storage) {
        try {
            storage.deleteAll();
        } catch (Exception ignored) {
        }

        if (testDir != null && Files.exists(testDir)) {
            try (Stream<Path> paths = Files.walk(testDir)) {
                paths.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
            } catch (IOException ex) {
                LOGGER.warning("FileSystemStorageTest cleanup failed: " + ex.getMessage());
            }
        }
    }

    // ─── Implementation-specific tests ───────────────────────────────────────

    /**
     * Tests specific to {@link FileSystemStorage} covering branches not exercised
     * by the generic TCK: alternative constructors, input validation guards,
     * {@code toString()}, and the {@link Path}-based constructor.
     */
    @Nested
    @DisplayName("FileSystemStorage — implementation-specific")
    class FileSystemSpecificTests {

        @Test
        @DisplayName("String constructor with blank path throws IllegalArgumentException")
        void blankStringConstructorThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> new FileSystemStorage("   "),
                "A blank path must be rejected immediately");
        }

        @Test
        @DisplayName("String constructor with null path throws NullPointerException")
        void nullStringConstructorThrows() {
            assertThrows(NullPointerException.class,
                () -> new FileSystemStorage((String) null));
        }

        @Test
        @DisplayName("Path constructor with null throws NullPointerException")
        void nullPathConstructorThrows() {
            assertThrows(NullPointerException.class,
                () -> new FileSystemStorage((Path) null));
        }

        @Test
        @DisplayName("Path-based constructor creates and uses the directory correctly")
        void pathConstructorWorksCorrectly(@TempDir Path dir) {
            Path chainDir = dir.resolve("path-ctor-chain");
            FileSystemStorage storage = new FileSystemStorage(chainDir);
            Block genesis = GenesisBlockFactory.create("path-ctor-test");
            storage.saveBlock(genesis);
            assertTrue(storage.exists(genesis.getHash()),
                "Path-based constructor must produce a working storage instance");
            storage.deleteAll();
        }

        @Test
        @DisplayName("toString() contains 'FileSystemStorage'")
        void toStringContainsClassName(@TempDir Path dir) {
            FileSystemStorage storage = new FileSystemStorage(dir.resolve("ts-chain"));
            assertTrue(storage.toString().contains("FileSystemStorage"),
                "toString() must identify the implementation class");
            storage.deleteAll();
        }

        @Test
        @DisplayName("toString() contains the configured directory path")
        void toStringContainsPath(@TempDir Path dir) {
            Path chainDir = dir.resolve("my-chain");
            FileSystemStorage storage = new FileSystemStorage(chainDir);
            assertTrue(storage.toString().contains("my-chain"),
                "toString() must include the storage directory path");
            storage.deleteAll();
        }

        @Test
        @DisplayName("saveBlock() throws NullPointerException for null block")
        void saveNullBlockThrows(@TempDir Path dir) {
            FileSystemStorage storage = new FileSystemStorage(dir.resolve("null-test"));
            assertThrows(NullPointerException.class, () -> storage.saveBlock(null));
            storage.deleteAll();
        }

        @Test
        @DisplayName("loadBlockByHash() throws NullPointerException for null hash")
        void loadBlockByHashNullThrows(@TempDir Path dir) {
            FileSystemStorage storage = new FileSystemStorage(dir.resolve("null-hash-test"));
            assertThrows(NullPointerException.class,
                () -> storage.loadBlockByHash(null));
            storage.deleteAll();
        }

        @Test
        @DisplayName("exists() throws NullPointerException for null hash")
        void existsNullThrows(@TempDir Path dir) {
            FileSystemStorage storage = new FileSystemStorage(dir.resolve("exists-null-test"));
            assertThrows(NullPointerException.class, () -> storage.exists(null));
            storage.deleteAll();
        }

        @Test
        @DisplayName("fromBytes() on a corrupted block file throws BlockValidationException")
        void corruptedBlockFileThrows(@TempDir Path dir) throws IOException {
            Path chainDir = dir.resolve("corrupt-chain");
            FileSystemStorage storage = new FileSystemStorage(chainDir);
            Block genesis = GenesisBlockFactory.create("corrupt-test");
            storage.saveBlock(genesis);

            // Overwrite the block file with garbage JSON
            Path blockFile = chainDir.resolve("block-0000000000.json");
            Files.writeString(blockFile, "{ corrupted: true }");

            // loadBlock should throw because the JSON is invalid / hash won't match
            assertThrows(BlockValidationException.class,
                () -> storage.loadBlock(0),
                "Loading a corrupted block file must throw BlockValidationException");

            // Clean up
            storage.deleteAll();
        }
    }
}
