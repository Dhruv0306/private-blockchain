package com.privatechain.storage;

import com.privatechain.core.builder.GenesisBlockFactory;
import com.privatechain.core.model.Block;
import com.privatechain.core.spi.BlockchainStorage;
import com.privatechain.storage.memory.InMemoryStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the full {@link StorageContractTest} Technology Compatibility Kit (TCK)
 * against {@link InMemoryStorage}, plus implementation-specific tests that cover
 * paths not reachable through the abstract contract tests.
 *
 * <p>InMemoryStorage is backed by a {@link java.util.TreeMap} with a
 * {@link java.util.concurrent.locks.ReentrantReadWriteLock} and has no external
 * dependencies (FR-STOR-02, T-033).</p>
 *
 * @since 1.0.0
 */
@DisplayName("InMemoryStorage — TCK + unit tests")
class InMemoryStorageTest extends StorageContractTest {

    /**
     * Creates a fresh in-memory storage instance for each test.
     *
     * @return a new, empty {@link InMemoryStorage}
     */
    @Override
    protected BlockchainStorage createStorage() {
        return new InMemoryStorage();
    }

    /**
     * Clears all blocks from the in-memory store.
     *
     * @param storage the storage instance to reset
     */
    @Override
    protected void destroyStorage(BlockchainStorage storage) {
        storage.deleteAll();
    }

    // ─── Implementation-specific tests ───────────────────────────────────────

    /**
     * Tests that are specific to {@link InMemoryStorage} and cover branches not
     * exercised by the generic TCK (e.g. {@code toString()}, null-argument guards).
     */
    @Nested
    @DisplayName("InMemoryStorage — implementation-specific")
    class InMemorySpecificTests {

        @Test
        @DisplayName("toString() contains the word 'InMemoryStorage'")
        void toStringContainsClassName() {
            InMemoryStorage storage = new InMemoryStorage();
            assertTrue(storage.toString().contains("InMemoryStorage"),
                "toString() must identify the implementation class");
        }

        @Test
        @DisplayName("toString() reports correct block count after saves")
        void toStringReportsBlockCount() {
            InMemoryStorage storage = new InMemoryStorage();
            Block genesis = GenesisBlockFactory.create("tostring-test");
            storage.saveBlock(genesis);
            assertTrue(storage.toString().contains("1"),
                "toString() must reflect the number of stored blocks");
            storage.deleteAll();
        }

        @Test
        @DisplayName("saveBlock() throws NullPointerException for null block")
        void saveNullBlockThrows() {
            InMemoryStorage storage = new InMemoryStorage();
            assertThrows(NullPointerException.class, () -> storage.saveBlock(null));
        }

        @Test
        @DisplayName("loadBlockByHash() throws NullPointerException for null hash")
        void loadBlockByHashNullThrows() {
            InMemoryStorage storage = new InMemoryStorage();
            assertThrows(NullPointerException.class,
                () -> storage.loadBlockByHash(null));
        }

        @Test
        @DisplayName("exists() throws NullPointerException for null hash")
        void existsNullThrows() {
            InMemoryStorage storage = new InMemoryStorage();
            assertThrows(NullPointerException.class, () -> storage.exists(null));
        }
    }
}
