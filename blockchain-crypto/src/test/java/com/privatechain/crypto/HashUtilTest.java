package com.privatechain.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link HashUtil}.
 *
 * <p>SHA-256 vectors are taken from NIST FIPS 180-4.
 * SHA3-256 vectors are from NIST FIPS 202.
 */
@DisplayName("HashUtil")
class HashUtilTest {

    // ── SHA-256 ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("sha256(String)")
    class Sha256StringTests {

        @Test
        @DisplayName("NIST vector: empty string")
        void emptyString() {
            assertEquals(
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                HashUtil.sha256("")
            );
        }

        @Test
        @DisplayName("NIST vector: 'abc'")
        void abc() {
            assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                HashUtil.sha256("abc")
            );
        }

        @Test
        @DisplayName("NIST vector: 448-bit message")
        void longerMessage() {
            assertEquals(
                "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
                HashUtil.sha256("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq")
            );
        }

        @Test
        @DisplayName("output is always 64 hex characters (256 bits)")
        void outputLength() {
            assertEquals(64, HashUtil.sha256("any input").length());
        }

        @Test
        @DisplayName("output is always lowercase hex")
        void outputIsLowercaseHex() {
            final String result = HashUtil.sha256("test");
            assertTrue(result.matches("[0-9a-f]{64}"),
                "Expected lowercase hex but got: " + result);
        }

        @Test
        @DisplayName("deterministic — same input always gives same output")
        void deterministic() {
            assertEquals(HashUtil.sha256("hello"), HashUtil.sha256("hello"));
        }

        @Test
        @DisplayName("different inputs give different outputs")
        void avalanche() {
            assertNotEquals(HashUtil.sha256("hello"), HashUtil.sha256("hello "));
        }

        @Test
        @DisplayName("null input throws IllegalArgumentException")
        void nullThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> HashUtil.sha256((String) null));
        }
    }

    @Nested
    @DisplayName("sha256(byte[])")
    class Sha256BytesTests {

        @Test
        @DisplayName("empty byte array matches empty string hash")
        void emptyBytes() {
            assertEquals(
                HashUtil.sha256(""),
                HashUtil.sha256(new byte[0])
            );
        }

        @Test
        @DisplayName("null input throws IllegalArgumentException")
        void nullThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> HashUtil.sha256((byte[]) null));
        }
    }

    // ── SHA3-256 ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("sha3_256(String)")
    class Sha3256Tests {

        @Test
        @DisplayName("NIST vector: empty string")
        void emptyString() {
            assertEquals(
                "a7ffc6f8bf1ed76651c14756a061d662f580ff4de43b49fa82d80a4b80f8434a",
                HashUtil.sha3_256("")
            );
        }

        @Test
        @DisplayName("NIST vector: 'abc'")
        void abc() {
            assertEquals(
                "3a985da74fe225b2045c172d6bd390bd855f086e3e9d525b46bfe24511431532",
                HashUtil.sha3_256("abc")
            );
        }

        @Test
        @DisplayName("output length is 64 hex characters")
        void outputLength() {
            assertEquals(64, HashUtil.sha3_256("test").length());
        }

        @Test
        @DisplayName("SHA3-256 differs from SHA-256 for same input")
        void differFromSha256() {
            assertNotEquals(HashUtil.sha256("abc"), HashUtil.sha3_256("abc"));
        }

        @Test
        @DisplayName("null input throws IllegalArgumentException")
        void nullThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> HashUtil.sha3_256(null));
        }
    }

    // ── Double SHA-256 ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("doubleSha256(String)")
    class DoubleSha256Tests {

        @Test
        @DisplayName("equals sha256(sha256(input))")
        void equalsManualDouble() {
            final String input = "blockchain";
            final String manual = HashUtil.sha256(HashUtil.hexToBytes(HashUtil.sha256(input)));
            assertEquals(manual, HashUtil.doubleSha256(input));
        }

        @Test
        @DisplayName("differs from single sha256")
        void differsFromSingle() {
            assertNotEquals(HashUtil.sha256("test"), HashUtil.doubleSha256("test"));
        }

        @Test
        @DisplayName("null input throws IllegalArgumentException")
        void nullThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> HashUtil.doubleSha256(null));
        }
    }

    // ── RIPEMD-160 ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ripemd160(byte[])")
    class Ripemd160Tests {

        @Test
        @DisplayName("output is always 20 bytes (160 bits)")
        void outputLength() {
            final byte[] result = HashUtil.ripemd160("test".getBytes());
            assertEquals(20, result.length);
        }

        @Test
        @DisplayName("known vector: empty input")
        void emptyInput() {
            // RIPEMD-160("") = 9c1185a5c5e9fc54612808977ee8f548b2258d31
            final byte[] result = HashUtil.ripemd160(new byte[0]);
            assertEquals("9c1185a5c5e9fc54612808977ee8f548b2258d31",
                HashUtil.bytesToHex(result));
        }

        @Test
        @DisplayName("known vector: 'abc'")
        void abc() {
            // RIPEMD-160("abc") = 8eb208f7e05d987a9b044a8e98c6b087f15a0bfc
            final byte[] result = HashUtil.ripemd160("abc".getBytes());
            assertEquals("8eb208f7e05d987a9b044a8e98c6b087f15a0bfc",
                HashUtil.bytesToHex(result));
        }

        @Test
        @DisplayName("deterministic — same input same output")
        void deterministic() {
            assertArrayEquals(
                HashUtil.ripemd160("hello".getBytes()),
                HashUtil.ripemd160("hello".getBytes())
            );
        }

        @Test
        @DisplayName("null input throws IllegalArgumentException")
        void nullThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> HashUtil.ripemd160(null));
        }
    }

    // ── bytesToHex / hexToBytes ───────────────────────────────────────────────

    @Nested
    @DisplayName("bytesToHex / hexToBytes")
    class HexConversionTests {

        @ParameterizedTest(name = "bytes {0} -> hex {1}")
        @CsvSource({
            "'',        ''",
            "'ff',      'ff'",
            "'00',      '00'",
            "'deadbeef','deadbeef'"
        })
        @DisplayName("bytesToHex round-trips correctly")
        void bytesToHexRoundtrip(final String hexInput, final String expectedHex) {
            if (hexInput.isEmpty()) {
                assertEquals("", HashUtil.bytesToHex(new byte[0]));
                return;
            }
            final byte[] bytes = HashUtil.hexToBytes(hexInput);
            assertEquals(expectedHex, HashUtil.bytesToHex(bytes));
        }

        @Test
        @DisplayName("hexToBytes rejects odd-length strings")
        void oddLengthThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> HashUtil.hexToBytes("abc"));
        }

        @Test
        @DisplayName("hexToBytes null throws")
        void hexToBytesNullThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> HashUtil.hexToBytes(null));
        }

        @Test
        @DisplayName("bytesToHex null throws")
        void bytesToHexNullThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> HashUtil.bytesToHex(null));
        }

        @Test
        @DisplayName("full round-trip: sha256 bytes -> hex -> bytes is unchanged")
        void sha256RoundTrip() {
            final byte[] original = HashUtil.hexToBytes(HashUtil.sha256("test"));
            assertEquals(32, original.length);
            final String backToHex = HashUtil.bytesToHex(original);
            assertEquals(HashUtil.sha256("test"), backToHex);
        }
    }
}
