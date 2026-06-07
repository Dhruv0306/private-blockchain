package com.privatechain.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.privatechain.core.builder.Blockchain;
import com.privatechain.core.exception.BlockValidationException;
import com.privatechain.core.exception.BlockchainException;
import com.privatechain.core.model.Block;
import com.privatechain.core.model.Transaction;
import com.privatechain.core.spi.BlockchainStorage;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Utility class for exporting and importing a full blockchain in JSON and CSV formats.
 *
 * <p>Provides three static operations that satisfy FR-SER-02 and FR-SER-03:</p>
 * <ul>
 *   <li>{@link #toJson(Blockchain)} — serializes all blocks to a JSON array string using
 *       the shared {@link BlockSerializer#MAPPER} instance, preserving full Jackson
 *       polymorphism for {@link Transaction} subtypes ({@code @JsonTypeInfo}).</li>
 *   <li>{@link #fromJson(String, BlockchainStorage)} — deserializes a JSON array produced
 *       by {@code toJson} and persists each block to the supplied storage backend after
 *       hash-integrity verification (NFR-SEC-03). Intended for administrative chain import
 *       and fast node bootstrap without network sync.</li>
 *   <li>{@link #toCsv(Blockchain)} — flattens the chain to one RFC-4180 CSV row per
 *       transaction across all blocks, suitable for audit exports and analytics pipelines.</li>
 * </ul>
 *
 * <p>This class is placed in {@code blockchain-storage} rather than {@code blockchain-core}
 * because it depends on {@link BlockSerializer#MAPPER} — a Jackson {@code ObjectMapper} that
 * is already a mandatory (non-optional) dependency of the storage module. Placing it here
 * keeps {@code blockchain-core} free of mandatory Jackson transitive dependencies
 * (design.md §7.1).</p>
 *
 * <h2>Thread safety</h2>
 * <p>All methods are stateless and thread-safe. The underlying {@link BlockSerializer#MAPPER}
 * is thread-safe once configured.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Export
 * String json = ChainExporter.toJson(node.getChain());
 * String csv  = ChainExporter.toCsv(node.getChain());
 *
 * // Import into a fresh storage backend
 * InMemoryStorage fresh = new InMemoryStorage();
 * ChainExporter.fromJson(json, fresh);
 * }</pre>
 *
 * @see BlockSerializer
 * @see Blockchain
 * @since 1.0.0
 */
public final class ChainExporter {

    // ─── CSV constants ────────────────────────────────────────────────────────

    /**
     * RFC-4180 CSV header row for {@link #toCsv(Blockchain)}.
     * Columns: block position, block identity, transaction identity, and financial fields.
     */
    private static final String CSV_HEADER =
        "blockIndex,blockHash,txId,senderAddress,receiverAddress,amount,timestamp,txType";

    /**
     * Utility class — no instances.
     */
    private ChainExporter() {
        throw new UnsupportedOperationException("ChainExporter is a utility class");
    }

    // ─── JSON export ──────────────────────────────────────────────────────────

    /**
     * Serializes the entire blockchain to a compact JSON array string (FR-SER-02).
     *
     * <p>Each element of the array is the canonical JSON representation of one {@link Block},
     * produced using the same {@link BlockSerializer#MAPPER} shared by all persistent storage
     * implementations. The resulting string can be restored via
     * {@link #fromJson(String, BlockchainStorage)}.</p>
     *
     * <p>Transaction polymorphism is fully preserved: each transaction object carries its
     * concrete class name in the {@code _type} field (Jackson {@code @JsonTypeInfo}), so
     * all subtype-specific fields survive the round-trip without registration (AC-09).</p>
     *
     * @param chain the blockchain to export (non-null)
     * @return compact JSON array string; never null or blank
     * @throws NullPointerException if {@code chain} is null
     * @throws BlockchainException  if Jackson serialization fails
     */
    public static String toJson(Blockchain chain) {
        Objects.requireNonNull(chain, "chain must not be null");

        List<Block> blocks = chain.getChain();
        try {
            // MAPPER is package-private in BlockSerializer; ChainExporter is in the same
            // package (com.privatechain.storage) so this access is intentional and valid.
            return BlockSerializer.MAPPER.writeValueAsString(blocks);
        } catch (IOException ex) {
            throw new BlockchainException(
                "ChainExporter.toJson failed: " + ex.getMessage(), ex) {
            };
        }
    }

    // ─── JSON import ──────────────────────────────────────────────────────────

    /**
     * Deserializes a JSON array produced by {@link #toJson} and persists each block
     * to the supplied storage backend (FR-SER-02).
     *
     * <p>Each deserialized block is hash-verified via {@link Block#isHashValid()} before
     * being saved to the storage. A hash mismatch causes a {@link BlockValidationException}
     * and aborts the import immediately, leaving already-saved blocks in the storage.
     * Callers that require transactional semantics should pass an empty storage and
     * call {@link BlockchainStorage#deleteAll()} on failure.</p>
     *
     * <p><strong>Note:</strong> This method bypasses {@code ConsensusEngine} validation
     * intentionally. Chain import is an administrative / bootstrap operation and must only
     * be used with trusted chain dumps. Normal block acceptance must go through
     * {@code Blockchain.addBlock()}, which enforces consensus rules.</p>
     *
     * @param json    a JSON array string produced by {@link #toJson} (non-null, non-blank)
     * @param storage the backend to write the imported blocks into (non-null)
     * @throws NullPointerException     if {@code json} or {@code storage} is null
     * @throws IllegalArgumentException if {@code json} is blank
     * @throws BlockValidationException if any block fails hash-integrity verification
     * @throws BlockchainException      if JSON parsing fails
     */
    public static void fromJson(String json, BlockchainStorage storage) {
        Objects.requireNonNull(json, "json must not be null");
        Objects.requireNonNull(storage, "storage must not be null");
        if (json.isBlank()) {
            throw new IllegalArgumentException("json must not be blank");
        }

        List<Block> blocks;
        try {
            blocks = BlockSerializer.MAPPER.readValue(
                json, new TypeReference<>() {
                });
        } catch (IOException ex) {
            throw new BlockchainException(
                "ChainExporter.fromJson failed to parse JSON: " + ex.getMessage(), ex) {
            };
        }

        // Persist each verified block in ascending index order
        for (Block block : blocks) {
            // Hash-integrity guard — equivalent to the check in BlockSerializer.verifyHash()
            // but done here directly to avoid accessing the private helper (NFR-SEC-03).
            if (!block.isHashValid()) {
                throw new BlockValidationException(
                    "Import aborted: hash mismatch for block at index " + block.getIndex()
                        + ". The chain dump may be corrupt.",
                    block);
            }
            storage.saveBlock(block);
        }
    }

    // ─── CSV export ───────────────────────────────────────────────────────────

    /**
     * Exports the blockchain as a flat RFC-4180 CSV string for audit and analytics use
     * (FR-SER-03).
     *
     * <p>The output contains one row per transaction across all blocks. The first row is
     * always the header. If the chain contains no transactions at all, only the header row
     * is returned. String fields that contain a comma ({@code ,}) are automatically wrapped
     * in double-quotes per RFC-4180 §2.6.</p>
     *
     * <p>Columns:</p>
     * <ol>
     *   <li>{@code blockIndex}    — zero-based block position in the chain</li>
     *   <li>{@code blockHash}     — full hex SHA-256 hash of the block</li>
     *   <li>{@code txId}          — transaction UUID</li>
     *   <li>{@code senderAddress} — sender's blockchain address</li>
     *   <li>{@code receiverAddress} — receiver's blockchain address</li>
     *   <li>{@code amount}        — token amount ({@link java.math.BigDecimal#toPlainString()})</li>
     *   <li>{@code timestamp}     — ISO-8601 UTC timestamp of the transaction</li>
     *   <li>{@code txType}        — simple class name of the concrete {@link Transaction} subtype</li>
     * </ol>
     *
     * @param chain the blockchain to export (non-null)
     * @return RFC-4180 CSV string with a header row; never null; never empty (always has header)
     * @throws NullPointerException if {@code chain} is null
     */
    public static String toCsv(Blockchain chain) {
        Objects.requireNonNull(chain, "chain must not be null");

        StringBuilder sb = new StringBuilder();
        sb.append(CSV_HEADER).append('\n');

        for (Block block : chain.getChain()) {
            for (Transaction tx : block.getTransactions()) {
                sb.append(block.getIndex()).append(',');
                sb.append(block.getHash()).append(',');
                sb.append(tx.getId()).append(',');
                sb.append(csvEscape(tx.getSenderAddress())).append(',');
                sb.append(csvEscape(tx.getReceiverAddress())).append(',');
                sb.append(tx.getAmount().toPlainString()).append(',');
                sb.append(tx.getTimestamp()).append(',');
                sb.append(tx.getClass().getSimpleName()).append('\n');
            }
        }

        return sb.toString();
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Wraps a CSV field value in double-quotes if it contains a comma, double-quote,
     * or newline character, per RFC-4180 §2.6 and §2.7.
     *
     * @param value the field value to escape (non-null)
     * @return the original value if no special characters are present, or the
     * double-quote-wrapped, internally-escaped form otherwise
     */
    private static String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        // RFC-4180: if a field contains a comma, double-quote, or line break it must be
        // enclosed in double-quotes. Any double-quote within the field is escaped as "".
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
