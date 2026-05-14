package com.privatechain.storage;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.privatechain.core.exception.BlockValidationException;
import com.privatechain.core.model.Block;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Shared Jackson-based JSON serializer / deserializer for {@link Block} objects.
 *
 * <p>Used internally by all persistent storage implementations ({@code LevelDBStorage},
 * {@code RocksDBStorage}, {@code FileSystemStorage}) to convert blocks to / from their
 * JSON wire format. Centralizing serialization here ensures that all implementations
 * use an identical format, making cross-implementation round-trips lossless.</p>
 *
 * <h2>Jackson configuration</h2>
 * <ul>
 *   <li>{@link JavaTimeModule} — serializes {@code Instant} as ISO-8601 strings, not
 *       numeric timestamp arrays, when {@code WRITE_DATES_AS_TIMESTAMPS} is disabled.</li>
 *   <li>{@link DeserializationFeature#FAIL_ON_UNKNOWN_PROPERTIES} is disabled so that
 *       convenience getters on {@link Block} (e.g. {@code getMerkleRoot()}, which
 *       delegates to {@code header.merkleRoot()}) do not cause deserialization failures.
 *       Jackson serializes every public getter as a JSON field; those derived fields
 *       have no corresponding constructor parameter in {@code Block}'s
 *       {@code @JsonCreator} and would otherwise trigger "Unrecognized field" errors.
 *       Disabling this feature is safe because {@link #verifyHash(Block)} provides
 *       an independent integrity check after every deserialization (NFR-SEC-03).</li>
 *   <li>Getter-based serialization is suppressed ({@code PropertyAccessor.GETTER}
 *       and {@code IS_GETTER} set to {@code NONE}) so only {@code @JsonProperty}-annotated
 *       constructor parameters are written to JSON. This prevents derived convenience
 *       getters such as {@code getMerkleRoot()} from producing redundant top-level
 *       fields, keeping stored JSON canonical and eliminating the round-trip mismatch
 *       at the source rather than merely tolerating it on the read path.</li>
 *   <li>Polymorphic {@code Transaction} subtypes are handled by Jackson's
 *       {@code @JsonTypeInfo} annotation on the abstract {@code Transaction} base class
 *       (design §7.5, FR-SER-01).</li>
 * </ul>
 *
 * @since 1.0.0
 */
public final class BlockSerializer {

    /**
     * Singleton, thread-safe {@link ObjectMapper} shared by all storage implementations.
     *
     * <p>{@link ObjectMapper} is thread-safe once fully configured and expensive to
     * construct, so a single shared instance is preferred over per-operation creation.</p>
     */
    static final ObjectMapper MAPPER;

    static {
        MAPPER = new ObjectMapper();

        // ── Time handling ────────────────────────────────────────────────────────
        // Register JavaTimeModule so Instant serializes as an ISO-8601 string
        // (e.g. "2026-04-22T10:15:30Z") rather than a [seconds, nanos] array.
        MAPPER.registerModule(new JavaTimeModule());
        MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // ── Unknown properties on deserialization ────────────────────────────────
        // Block exposes convenience getters (getMerkleRoot, getTimestamp, …) that
        // Jackson serializes as top-level JSON fields by default. Those fields have
        // no matching @JsonProperty constructor parameter, so deserialization would
        // fail with "Unrecognized field" under the default FAIL_ON_UNKNOWN_PROPERTIES.
        // Disabling this feature makes deserialization tolerant of such derived fields.
        // Integrity is independently guaranteed by verifyHash() after every fromBytes().
        MAPPER.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        // ── Visibility: suppress getter-driven serialization ─────────────────────
        // Do NOT serialize public getters automatically — only serialize fields that
        // carry an explicit @JsonProperty on the @JsonCreator constructor. This stops
        // derived getters (e.g. getMerkleRoot → header.merkleRoot()) from writing
        // redundant top-level fields into stored JSON, keeping the format canonical.
        MAPPER.setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE);
        MAPPER.setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE);

        // Allow Jackson to read private fields so Transaction subtype fields are
        // accessible without requiring public getters on every custom subclass.
        MAPPER.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    }

    /**
     * Utility class — no instances.
     */
    private BlockSerializer() {
        throw new UnsupportedOperationException("BlockSerializer is a utility class");
    }

    /**
     * Serializes a {@link Block} to its canonical UTF-8 JSON byte representation.
     *
     * <p>Compact JSON (no pretty-printing) minimizes storage footprint, particularly
     * in LevelDB and RocksDB where values are raw byte arrays.</p>
     *
     * @param block the block to serialize (non-null)
     * @return UTF-8 JSON byte array (non-null)
     * @throws BlockValidationException if Jackson serialization fails
     * @throws NullPointerException     if {@code block} is null
     */
    public static byte[] toBytes(Block block) {
        Objects.requireNonNull(block, "block must not be null");
        try {
            return MAPPER.writeValueAsBytes(block);
        } catch (IOException ex) {
            throw new BlockValidationException(
                "Failed to serialize block at index " + block.getIndex()
                    + ": " + ex.getMessage(),
                ex, null);
        }
    }

    /**
     * Serializes a {@link Block} to a UTF-8 JSON string.
     *
     * <p>Convenience wrapper around {@link #toBytes(Block)} for storage backends
     * that work with {@code String} values (e.g., {@code FileSystemStorage}).</p>
     *
     * @param block the block to serialize (non-null)
     * @return JSON string (non-null)
     * @throws BlockValidationException if Jackson serialization fails
     * @throws NullPointerException     if {@code block} is null
     */
    public static String toJson(Block block) {
        return new String(toBytes(block), StandardCharsets.UTF_8);
    }

    /**
     * Deserializes a {@link Block} from a raw UTF-8 JSON byte array.
     *
     * <p>After deserialization, the block's hash is recomputed and compared against
     * the stored {@code hash} field to detect storage corruption (NFR-SEC-03).
     * A mismatch causes a {@link BlockValidationException}.</p>
     *
     * @param bytes raw UTF-8 JSON bytes (non-null, non-empty)
     * @return the deserialized and hash-verified {@link Block}
     * @throws BlockValidationException if deserialization fails or hash is corrupt
     * @throws NullPointerException     if {@code bytes} is null
     */
    public static Block fromBytes(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes must not be null");
        try {
            Block block = MAPPER.readValue(bytes, Block.class);
            verifyHash(block);
            return block;
        } catch (IOException ex) {
            throw new BlockValidationException(
                "Failed to deserialize block from JSON: " + ex.getMessage(),
                ex, null);
        }
    }

    /**
     * Deserializes a {@link Block} from a UTF-8 JSON string.
     *
     * <p>Convenience wrapper around {@link #fromBytes(byte[])} for storage backends
     * that read {@code String} values (e.g., {@code FileSystemStorage}).</p>
     *
     * @param json UTF-8 JSON string (non-null, non-blank)
     * @return the deserialized and hash-verified {@link Block}
     * @throws BlockValidationException if deserialization fails or hash is corrupt
     * @throws NullPointerException     if {@code json} is null
     */
    public static Block fromJson(String json) {
        Objects.requireNonNull(json, "json must not be null");
        return fromBytes(json.getBytes(StandardCharsets.UTF_8));
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Recomputes the block hash and verifies it against the stored {@code hash} field.
     *
     * <p>Implements NFR-SEC-03: block hashes MUST be recomputed on chain load, and a
     * mismatch MUST throw an exception. This is the storage layer's independent guard
     * against silent data corruption — it runs even when
     * {@code FAIL_ON_UNKNOWN_PROPERTIES} is disabled.</p>
     *
     * @param block the block whose hash is to be verified
     * @throws BlockValidationException if the stored hash does not match the recomputed hash
     */
    private static void verifyHash(Block block) {
        if (!block.isHashValid()) {
            throw new BlockValidationException(
                "Storage corruption detected: hash mismatch for block at index "
                    + block.getIndex()
                    + ". Stored hash=" + block.getHash().substring(0, 16) + "...",
                null);
        }
    }
}
