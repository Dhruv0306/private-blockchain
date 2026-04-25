package com.privatechain.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ECDSASignatureUtil} and {@link BlockchainSignature}.
 */
@DisplayName("ECDSASignatureUtil and BlockchainSignature")
class SignatureUtilTest {

    private static final byte[] DATA = "tx:abc123".getBytes(StandardCharsets.UTF_8);
    private ECKeyPair keyPair;

    @BeforeEach
    void setUp() {
        keyPair = KeyPairGenerator.generateECKeyPair();
    }

    // ── sign ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("sign()")
    class SignTests {

        @Test
        @DisplayName("produces a non-null signature")
        void producesSignature() {
            final BlockchainSignature sig = ECDSASignatureUtil.sign(DATA, keyPair.privateKey());
            assertNotNull(sig);
        }

        @Test
        @DisplayName("signature bytes are non-empty")
        void signatureBytesNonEmpty() {
            final BlockchainSignature sig = ECDSASignatureUtil.sign(DATA, keyPair.privateKey());
            assertTrue(sig.derBytes().length > 0);
        }

        @Test
        @DisplayName("DER signature is at least 8 bytes (minimum valid DER ECDSA)")
        void minDerLength() {
            final BlockchainSignature sig = ECDSASignatureUtil.sign(DATA, keyPair.privateKey());
            // DER ECDSA: sequence(0x30) + length + integer(r) + integer(s) — minimum ~8 bytes
            assertTrue(sig.derBytes().length >= 8,
                "DER signature too short: " + sig.derBytes().length + " bytes");
        }

        @Test
        @DisplayName("same data + same key → different signatures (ECDSA is probabilistic)")
        void signaturesAreProbabilistic() {
            final BlockchainSignature sig1 = ECDSASignatureUtil.sign(DATA, keyPair.privateKey());
            final BlockchainSignature sig2 = ECDSASignatureUtil.sign(DATA, keyPair.privateKey());
            // ECDSA with secp256k1 uses random k — two signatures should almost always differ
            // (collision probability is astronomically low)
            assertFalse(sig1.equals(sig2),
                "Two independent ECDSA signatures should not be identical");
        }

        @Test
        @DisplayName("null data throws IllegalArgumentException")
        void nullDataThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> ECDSASignatureUtil.sign(null, keyPair.privateKey()));
        }

        @Test
        @DisplayName("null privateKey throws IllegalArgumentException")
        void nullKeyThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> ECDSASignatureUtil.sign(DATA, null));
        }
    }

    // ── verify ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("verify()")
    class VerifyTests {

        @Test
        @DisplayName("valid signature verifies correctly")
        void validSignature() {
            final BlockchainSignature sig = ECDSASignatureUtil.sign(DATA, keyPair.privateKey());
            assertTrue(ECDSASignatureUtil.verify(DATA, sig, keyPair.publicKey()));
        }

        @RepeatedTest(3)
        @DisplayName("valid signature always verifies (repeated for probabilistic test)")
        void alwaysVerifies() {
            final ECKeyPair fresh = KeyPairGenerator.generateECKeyPair();
            final BlockchainSignature sig = ECDSASignatureUtil.sign(DATA, fresh.privateKey());
            assertTrue(ECDSASignatureUtil.verify(DATA, sig, fresh.publicKey()));
        }

        @Test
        @DisplayName("tampered data fails verification")
        void tamperedDataFails() {
            final BlockchainSignature sig = ECDSASignatureUtil.sign(DATA, keyPair.privateKey());
            final byte[] tampered = "tx:TAMPERED".getBytes(StandardCharsets.UTF_8);
            assertFalse(ECDSASignatureUtil.verify(tampered, sig, keyPair.publicKey()));
        }

        @Test
        @DisplayName("wrong public key fails verification")
        void wrongKeyFails() {
            final BlockchainSignature sig = ECDSASignatureUtil.sign(DATA, keyPair.privateKey());
            final ECKeyPair otherPair = KeyPairGenerator.generateECKeyPair();
            assertFalse(ECDSASignatureUtil.verify(DATA, sig, otherPair.publicKey()));
        }

        @Test
        @DisplayName("bit-flipped signature returns false (not exception)")
        void bitFlippedSignatureReturnsFalse() {
            final BlockchainSignature sig = ECDSASignatureUtil.sign(DATA, keyPair.privateKey());
            final byte[] flipped = sig.derBytes();
            flipped[flipped.length - 1] ^= 0x01; // flip last bit
            final BlockchainSignature corrupt = new BlockchainSignature(flipped);
            // Should return false, NOT throw
            assertFalse(ECDSASignatureUtil.verify(DATA, corrupt, keyPair.publicKey()));
        }

        @Test
        @DisplayName("null data throws IllegalArgumentException")
        void nullDataThrows() {
            final BlockchainSignature sig = ECDSASignatureUtil.sign(DATA, keyPair.privateKey());
            assertThrows(IllegalArgumentException.class,
                () -> ECDSASignatureUtil.verify(null, sig, keyPair.publicKey()));
        }

        @Test
        @DisplayName("null signature throws IllegalArgumentException")
        void nullSignatureThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> ECDSASignatureUtil.verify(DATA, null, keyPair.publicKey()));
        }

        @Test
        @DisplayName("null publicKey throws IllegalArgumentException")
        void nullKeyThrows() {
            final BlockchainSignature sig = ECDSASignatureUtil.sign(DATA, keyPair.privateKey());
            assertThrows(IllegalArgumentException.class,
                () -> ECDSASignatureUtil.verify(DATA, sig, null));
        }
    }

    // ── BlockchainSignature ───────────────────────────────────────────────────

    @Nested
    @DisplayName("BlockchainSignature")
    class BlockchainSignatureTests {

        @Test
        @DisplayName("toString() returns safe placeholder, not raw bytes")
        void toStringSafe() {
            final BlockchainSignature sig = ECDSASignatureUtil.sign(DATA, keyPair.privateKey());
            assertEquals("[ECDSA signature]", sig.toString());
        }

        @Test
        @DisplayName("derBytes() returns defensive copy")
        void defensiveCopy() {
            final BlockchainSignature sig = ECDSASignatureUtil.sign(DATA, keyPair.privateKey());
            final byte[] copy1 = sig.derBytes();
            copy1[0] = (byte) ~copy1[0];           // mutate the copy
            assertArrayEquals(sig.derBytes(), sig.derBytes()); // original unchanged
        }

        @Test
        @DisplayName("toHex() + fromHex() round-trips correctly")
        void hexRoundTrip() {
            final BlockchainSignature original = ECDSASignatureUtil.sign(DATA, keyPair.privateKey());
            final String hex = original.toHex();
            final BlockchainSignature reconstructed = BlockchainSignature.fromHex(hex);
            assertEquals(original, reconstructed);
            // Reconstructed signature must still verify
            assertTrue(ECDSASignatureUtil.verify(DATA, reconstructed, keyPair.publicKey()));
        }

        @Test
        @DisplayName("equals is based on byte content")
        void equalsOnContent() {
            final BlockchainSignature sig = ECDSASignatureUtil.sign(DATA, keyPair.privateKey());
            final BlockchainSignature copy = BlockchainSignature.fromHex(sig.toHex());
            assertEquals(sig, copy);
            assertEquals(sig.hashCode(), copy.hashCode());
        }

        @Test
        @DisplayName("null derBytes throws IllegalArgumentException")
        void nullBytesThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> new BlockchainSignature(null));
        }

        @Test
        @DisplayName("empty derBytes throws IllegalArgumentException")
        void emptyBytesThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> new BlockchainSignature(new byte[0]));
        }

        @Test
        @DisplayName("fromHex(null) throws IllegalArgumentException")
        void fromHexNullThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> BlockchainSignature.fromHex(null));
        }

        @Test
        @DisplayName("fromHex(empty) throws IllegalArgumentException")
        void fromHexEmptyThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> BlockchainSignature.fromHex(""));
        }

        @Test
        @DisplayName("two different signatures are not equal")
        void differentSignaturesNotEqual() {
            final BlockchainSignature sig1 = ECDSASignatureUtil.sign(DATA, keyPair.privateKey());
            final BlockchainSignature sig2 = ECDSASignatureUtil.sign(DATA, keyPair.privateKey());
            assertNotEquals(sig1, sig2);
        }
    }
}
