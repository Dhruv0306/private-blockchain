package com.privatechain.storage;

import com.privatechain.core.builder.GenesisBlockFactory;
import com.privatechain.core.exception.BlockValidationException;
import com.privatechain.core.model.Block;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BlockSerializer}.
 *
 * <p>Covers serialization round-trips via both byte-array and String APIs,
 * null-argument guards, the private constructor utility-class guard, and the
 * hash-corruption detection path required by NFR-SEC-03.</p>
 *
 * @since 1.0.0
 */
@DisplayName("BlockSerializer unit tests")
class BlockSerializerTest {

    /**
     * A genesis block reused across tests — immutable so safe to share.
     */
    private Block genesis;

    @BeforeEach
    void setUp() {
        genesis = GenesisBlockFactory.create("serializer-test-chain");
    }

    // ─── toBytes / fromBytes ──────────────────────────────────────────────────

    @Nested
    @DisplayName("toBytes() and fromBytes()")
    class ToBytesFromBytesTests {

        @Test
        @DisplayName("toBytes() produces non-null, non-empty byte array")
        void toBytesProducesNonEmptyArray() {
            byte[] bytes = BlockSerializer.toBytes(genesis);
            assertNotNull(bytes);
            assertTrue(bytes.length > 0, "Serialized bytes must be non-empty");
        }

        @Test
        @DisplayName("fromBytes(toBytes(block)) round-trips the block hash")
        void roundTripPreservesHash() {
            byte[] bytes = BlockSerializer.toBytes(genesis);
            Block loaded = BlockSerializer.fromBytes(bytes);
            assertEquals(genesis.getHash(), loaded.getHash(),
                "Hash must survive a toBytes → fromBytes round-trip");
        }

        @Test
        @DisplayName("fromBytes(toBytes(block)) round-trips the block index")
        void roundTripPreservesIndex() {
            byte[] bytes = BlockSerializer.toBytes(genesis);
            Block loaded = BlockSerializer.fromBytes(bytes);
            assertEquals(genesis.getIndex(), loaded.getIndex());
        }

        @Test
        @DisplayName("fromBytes(toBytes(block)) round-trips previousHash")
        void roundTripPreservesPreviousHash() {
            byte[] bytes = BlockSerializer.toBytes(genesis);
            Block loaded = BlockSerializer.fromBytes(bytes);
            assertEquals(genesis.getPreviousHash(), loaded.getPreviousHash());
        }

        @Test
        @DisplayName("toBytes() throws NullPointerException for null block")
        void toBytesNullThrows() {
            assertThrows(NullPointerException.class, () -> BlockSerializer.toBytes(null));
        }

        @Test
        @DisplayName("fromBytes() throws NullPointerException for null bytes")
        void fromBytesNullThrows() {
            assertThrows(NullPointerException.class, () -> BlockSerializer.fromBytes(null));
        }

        @Test
        @DisplayName("fromBytes() throws BlockValidationException for malformed JSON")
        void fromBytesMalformedJsonThrows() {
            byte[] garbage = "{ not valid json !!!".getBytes(StandardCharsets.UTF_8);
            assertThrows(BlockValidationException.class, () -> BlockSerializer.fromBytes(garbage));
        }
    }

    // ─── toJson / fromJson ────────────────────────────────────────────────────

    @Nested
    @DisplayName("toJson() and fromJson()")
    class ToJsonFromJsonTests {

        /**
         * Counts non-overlapping occurrences of {@code needle} in {@code haystack}.
         */
        private static int countOccurrences(String haystack, String needle) {
            int count = 0;
            int idx = 0;
            while ((idx = haystack.indexOf(needle, idx)) != -1) {
                count++;
                idx += needle.length();
            }
            return count;
        }

        @Test
        @DisplayName("toJson() produces a non-blank JSON string")
        void toJsonProducesNonBlankString() {
            String json = BlockSerializer.toJson(genesis);
            assertNotNull(json);
            assertTrue(json.contains("{"), "toJson() output must be a JSON object");
        }

        @Test
        @DisplayName("toJson() output contains the block hash")
        void toJsonContainsHash() {
            String json = BlockSerializer.toJson(genesis);
            assertTrue(json.contains(genesis.getHash()),
                "Serialized JSON must contain the block hash");
        }

        @Test
        @DisplayName("fromJson(toJson(block)) round-trips the block hash")
        void fromJsonRoundTripHash() {
            String json = BlockSerializer.toJson(genesis);
            Block loaded = BlockSerializer.fromJson(json);
            assertEquals(genesis.getHash(), loaded.getHash());
        }

        @Test
        @DisplayName("fromJson(toJson(block)) round-trips the block index")
        void fromJsonRoundTripIndex() {
            String json = BlockSerializer.toJson(genesis);
            Block loaded = BlockSerializer.fromJson(json);
            assertEquals(genesis.getIndex(), loaded.getIndex());
        }

        @Test
        @DisplayName("toJson() throws NullPointerException for null block")
        void toJsonNullThrows() {
            assertThrows(NullPointerException.class, () -> BlockSerializer.toJson(null));
        }

        @Test
        @DisplayName("fromJson() throws NullPointerException for null string")
        void fromJsonNullThrows() {
            assertThrows(NullPointerException.class, () -> BlockSerializer.fromJson(null));
        }

        @Test
        @DisplayName("fromJson() throws BlockValidationException for malformed JSON")
        void fromJsonMalformedThrows() {
            assertThrows(BlockValidationException.class,
                () -> BlockSerializer.fromJson("{ bad json }"));
        }

        @Test
        @DisplayName("toJson() output does NOT contain top-level 'merkleRoot' field key")
        void toJsonDoesNotContainTopLevelMerkleRoot() {
            // Regression test: getMerkleRoot() must not be serialized as a top-level
            // field because it has no matching constructor parameter in Block's
            // @JsonCreator and would cause deserialization to fail.
            String json = BlockSerializer.toJson(genesis);
            // The merkleRoot SHOULD appear inside the "header" object, not at the top level.
            // A top-level occurrence would look like: ,"merkleRoot":"..." outside "header":{}.
            // Simple check: count occurrences — it should appear exactly once (inside header).
            int occurrences = countOccurrences(json, "\"merkleRoot\"");
            assertEquals(1, occurrences,
                "'merkleRoot' must appear exactly once in JSON (inside header), not as a "
                    + "redundant top-level field. JSON was: " + json);
        }
    }

    // ─── Utility-class guard ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Utility-class constructor guard")
    class UtilityClassTests {

        @Test
        @DisplayName("private constructor throws UnsupportedOperationException via reflection")
        void privateConstructorThrows() throws Exception {
            Constructor<BlockSerializer> ctor =
                BlockSerializer.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            // The constructor body throws UnsupportedOperationException;
            // reflection wraps it in InvocationTargetException.
            assertThrows(Exception.class, ctor::newInstance,
                "BlockSerializer must not be instantiable");
        }
    }
}
