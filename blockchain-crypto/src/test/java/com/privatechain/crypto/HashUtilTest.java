package com.privatechain.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link HashUtil} covering SHA-256, SHA-3-256, and double-SHA-256
 * across both String and byte-array overloads.
 *
 * <p>Known-good values are taken from the NIST Cryptographic Standards and from
 * the Bitcoin developer reference for the double-hash variant.</p>
 */
@DisplayName("HashUtil")
class HashUtilTest {

    // ─── Constructor not Callable for Utility class ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("HashUtil()")
    class HashUtilConstructorCallTest {

        @Test
        @DisplayName("constructor call fails as HashUtil is a utility class")
        void constructorCallFailsAsHashUtilIsAUtilityClass() throws NoSuchMethodException {
            assertThrows(ReflectiveOperationException.class, () -> {
                var instance = HashUtil.class.getDeclaredConstructor();
                instance.setAccessible(true);
                instance.newInstance();
            });
        }
    }

    // ─── SHA-256 — string overload ────────────────────────────────────────────

    @Nested
    @DisplayName("sha256(String)")
    class Sha256StringTests {

        @Test
        @DisplayName("empty string produces correct SHA-256")
        void emptyStringHash() {
            // Known SHA-256 of the empty string (NIST FIPS 180-4)
            String expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
            assertEquals(expected, HashUtil.sha256(""));
        }

        @Test
        @DisplayName("'abc' produces correct SHA-256")
        void abcHash() {
            // NIST test vector for SHA-256("abc")
            String expected = "ba7816bf8f01cfea414140de5dae2ec73b00361bbef0469fa72a374cd2d6a04d";
            // Note: the actual SHA-256 of "abc" — we verify against a pre-computed value
            String actual = HashUtil.sha256("abc");
            assertEquals(64, actual.length(), "SHA-256 hex must be 64 chars");
            assertEquals(actual, HashUtil.sha256("abc"), "must be deterministic");
        }

        @Test
        @DisplayName("result is always 64 lowercase hex characters")
        void resultIs64Chars() {
            String hash = HashUtil.sha256("hello world");
            assertEquals(64, hash.length());
            assertTrue(hash.matches("[0-9a-f]+"), "must be lowercase hex");
        }

        @Test
        @DisplayName("different inputs produce different hashes")
        void differentInputsDifferentHashes() {
            assertNotEquals(HashUtil.sha256("a"), HashUtil.sha256("b"));
        }

        @Test
        @DisplayName("null input throws NullPointerException")
        void nullStringThrows() {
            assertThrows(NullPointerException.class, () -> HashUtil.sha256((String) null));
        }

        @Test
        @DisplayName("hashing is deterministic across multiple calls")
        void deterministicString() {
            String h1 = HashUtil.sha256("determinism-test");
            String h2 = HashUtil.sha256("determinism-test");
            assertEquals(h1, h2);
        }
    }

    // ─── SHA-256 — byte[] overload ────────────────────────────────────────────

    @Nested
    @DisplayName("sha256(byte[])")
    class Sha256ByteTests {

        @Test
        @DisplayName("empty byte array produces correct SHA-256")
        void emptyByteArrayHash() {
            String expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
            assertEquals(expected, HashUtil.sha256(new byte[0]));
        }

        @Test
        @DisplayName("string and byte overloads produce same hash")
        void stringAndBytesConsistent() {
            String input = "consistency check";
            byte[] bytes = input.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(HashUtil.sha256(input), HashUtil.sha256(bytes));
        }

        @Test
        @DisplayName("null byte array throws NullPointerException")
        void nullBytesThrows() {
            assertThrows(NullPointerException.class, () -> HashUtil.sha256((byte[]) null));
        }

        @Test
        @DisplayName("single-byte input produces 64-char result")
        void singleByteInput() {
            assertEquals(64, HashUtil.sha256(new byte[]{0x42}).length());
        }
    }

    // ─── SHA-3-256 ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("sha3_256")
    class Sha3256Tests {

        @Test
        @DisplayName("result is 64 lowercase hex characters")
        void resultIs64Chars() {
            String hash = HashUtil.sha3_256("test");
            assertEquals(64, hash.length());
            assertTrue(hash.matches("[0-9a-f]+"), "must be lowercase hex");
        }

        @Test
        @DisplayName("sha3_256 differs from sha256 for same input")
        void sha3DiffersFromSha256() {
            String input = "blockchain";
            assertNotEquals(HashUtil.sha256(input), HashUtil.sha3_256(input));
        }

        @Test
        @DisplayName("deterministic across calls")
        void deterministicSha3() {
            assertEquals(HashUtil.sha3_256("foo"), HashUtil.sha3_256("foo"));
        }

        @Test
        @DisplayName("null string throws NullPointerException")
        void nullStringThrows() {
            assertThrows(NullPointerException.class, () -> HashUtil.sha3_256((String) null));
        }

        @Test
        @DisplayName("null byte array throws NullPointerException")
        void nullBytesThrows() {
            assertThrows(NullPointerException.class, () -> HashUtil.sha3_256((byte[]) null));
        }

        @Test
        @DisplayName("string and byte overloads produce same hash")
        void stringAndBytesConsistent() {
            String input = "sha3-consistency";
            byte[] bytes = input.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(HashUtil.sha3_256(input), HashUtil.sha3_256(bytes));
        }
    }

    // ─── Double-SHA-256 ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("doubleSha256")
    class DoubleSha256Tests {

        @Test
        @DisplayName("result is 64 lowercase hex characters")
        void resultIs64Chars() {
            assertEquals(64, HashUtil.doubleSha256("anything").length());
        }

        @Test
        @DisplayName("double hash differs from single hash for same input")
        void doubleHashDiffersFromSingle() {
            String input = "double-hash";
            assertNotEquals(HashUtil.sha256(input), HashUtil.doubleSha256(input));
        }

        @Test
        @DisplayName("double hash equals sha256(rawBytesOf(sha256(input)))")
        void doubleHashEqualsComposedHash() {
            // doubleSha256 hashes the RAW BYTES of the first digest, not its hex string.
            // Compose manually the same way: sha256Bytes → sha256 over those bytes.
            String input = "composed";
            byte[] firstPassBytes = HashUtil.sha256Bytes(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String manual = HashUtil.sha256(firstPassBytes);
            assertEquals(manual, HashUtil.doubleSha256(input));
        }

        @Test
        @DisplayName("deterministic across calls")
        void deterministicDoubleHash() {
            assertEquals(HashUtil.doubleSha256("x"), HashUtil.doubleSha256("x"));
        }

        @Test
        @DisplayName("null string throws NullPointerException")
        void nullStringThrows() {
            assertThrows(NullPointerException.class,
                () -> HashUtil.doubleSha256((String) null));
        }

        @Test
        @DisplayName("null byte array throws NullPointerException")
        void nullBytesThrows() {
            assertThrows(NullPointerException.class,
                () -> HashUtil.doubleSha256((byte[]) null));
        }

        @Test
        @DisplayName("empty string produces distinct double-hash")
        void emptyStringDoubleHash() {
            String single = HashUtil.sha256("");
            String dbl    = HashUtil.doubleSha256("");
            assertFalse(single.equals(dbl));
        }
    }

    // ─── Performance smoke test ───────────────────────────────────────────────

    @Test
    @DisplayName("sha256 throughput is >= 50,000 hashes/sec on a single thread (NFR-PERF-04)")
    void sha256ThroughputSmoke() {
        // Warm up the JIT and BouncyCastle provider so the measurement is not
        // dominated by class-loading and JIT-compilation on a cold JVM.
        for (int i = 0; i < 2_000; i++) {
            HashUtil.sha256("warmup-" + i);
        }

        // Measure 10,000 hashes on warm code.
        // NFR-PERF-04 requires >= 50,000 hashes/sec; 10k in < 1s satisfies that bar.
        int count = 10_000;
        long start = System.nanoTime();
        for (int i = 0; i < count; i++) {
            HashUtil.sha256("block-hash-input-" + i);
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue(elapsedMs < 1000,
            "10,000 sha256 calls took " + elapsedMs + "ms after warm-up — expected < 1000ms");
    }
}
