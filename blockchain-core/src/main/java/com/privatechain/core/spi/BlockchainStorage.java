package com.privatechain.core.spi;

import com.privatechain.core.model.Block;

import java.util.List;
import java.util.Optional;

/**
 * Service Provider Interface (SPI) for persistent block storage.
 *
 * <p>Implementations must be thread-safe for concurrent reads; writes must be
 * serialized (NFR-REL-01, FR-STOR-06). The library ships four built-in
 * implementations in the {@code blockchain-storage} module:</p>
 * <ul>
 *   <li>{@code InMemoryStorage} — HashMap-backed, for testing (FR-STOR-02)</li>
 *   <li>{@code LevelDBStorage} — crash-safe persistent storage (FR-STOR-03)</li>
 *   <li>{@code RocksDBStorage} — high write-throughput persistent storage (FR-STOR-04)</li>
 *   <li>{@code FileSystemStorage} — one JSON file per block, zero native deps (FR-STOR-05)</li>
 * </ul>
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li>Blocks are stored by their integer index (unique per chain).</li>
 *   <li>{@link #saveBlock(Block)} is idempotent: saving the same block twice must not
 *       throw; the second call is a no-op or an overwrite of identical data.</li>
 *   <li>{@link #loadAll()} must return blocks in ascending index order.</li>
 * </ul>
 *
 * @see com.privatechain.core.builder.BlockchainConfig
 * @since 1.0.0
 */
public interface BlockchainStorage {

    /**
     * Persists a block to the underlying storage medium.
     *
     * <p>Must be atomic: either the block is fully written or the storage state
     * is unchanged (no partial writes). Implementations backed by LevelDB or
     * RocksDB satisfy this by writing inside an atomic batch.</p>
     *
     * @param block the block to persist (non-null)
     * @throws com.privatechain.core.exception.BlockValidationException if the write fails
     */
    void saveBlock(Block block);

    /**
     * Retrieves a block by its index.
     *
     * @param index block index (&ge; 0)
     * @return the block at the given index
     * @throws java.util.NoSuchElementException if no block exists at {@code index}
     */
    Block loadBlock(int index);

    /**
     * Retrieves a block by its SHA-256 hash.
     *
     * @param hash hex-encoded block hash (non-null)
     * @return an {@link Optional} containing the block, or empty if not found
     */
    Optional<Block> loadBlockByHash(String hash);

    /**
     * Returns all stored blocks in ascending index order.
     *
     * <p>For large chains callers should prefer iterating via index ranges rather
     * than loading the full chain into memory at once.</p>
     *
     * @return non-null, possibly empty, ordered list of all blocks
     */
    List<Block> loadAll();

    /**
     * Returns {@code true} if a block with the given hash is stored.
     *
     * @param hash hex-encoded block hash to look up (non-null)
     * @return {@code true} if present
     */
    boolean exists(String hash);

    /**
     * Returns the current number of stored blocks (i.e., the latest block index + 1).
     *
     * @return number of blocks (&ge; 0)
     */
    int chainHeight();

    /**
     * Removes all stored blocks from the storage medium.
     *
     * <p><strong>Warning:</strong> This operation is irreversible. It is intended
     * for testing and chain-reset scenarios only.</p>
     */
    void deleteAll();
}
