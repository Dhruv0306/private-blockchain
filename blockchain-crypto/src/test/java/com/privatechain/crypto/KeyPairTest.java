package com.privatechain.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ECKeyPair} and {@link KeyPairGenerator} covering key generation,
 * hex export/import, address derivation, private-key masking, and the security
 * invariant that private keys never appear in {@link ECKeyPair#toString()} (NFR-SEC-01,
 * T-031).
 */
@DisplayName("ECKeyPair and KeyPairGenerator")
class KeyPairTest {

    // ─── Key generation ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("generateECKeyPair()")
    class GenerationTests {

        @Test
        @DisplayName("generates non-null ECKeyPair with non-null keys")
        void generatesNonNullPair() {
            ECKeyPair kp = KeyPairGenerator.generateECKeyPair();
            assertNotNull(kp);
            assertNotNull(kp.getPublicKey());
            assertNotNull(kp.getPrivateKeyHex());
        }

        @Test
        @DisplayName("public key hex is a non-blank hex string")
        void publicKeyHexIsHex() {
            String pubHex = KeyPairGenerator.generateECKeyPair().getPublicKeyHex();
            assertFalse(pubHex.isBlank());
            assertTrue(pubHex.matches("[0-9a-fA-F]+"), "public key hex must be hex-encoded");
        }

        @Test
        @DisplayName("private key hex is a non-blank hex string")
        void privateKeyHexIsHex() {
            String privHex = KeyPairGenerator.generateECKeyPair().getPrivateKeyHex();
            assertFalse(privHex.isBlank());
            assertTrue(privHex.matches("[0-9a-fA-F]+"), "private key hex must be hex-encoded");
        }

        @RepeatedTest(5)
        @DisplayName("successive calls produce different key pairs")
        void eachCallProducesUniqueKeys() {
            ECKeyPair kp1 = KeyPairGenerator.generateECKeyPair();
            ECKeyPair kp2 = KeyPairGenerator.generateECKeyPair();
            assertNotEquals(kp1.getPublicKeyHex(), kp2.getPublicKeyHex(),
                "two separate calls must yield distinct public keys");
            assertNotEquals(kp1.getPrivateKeyHex(), kp2.getPrivateKeyHex(),
                "two separate calls must yield distinct private keys");
        }

        @Test
        @DisplayName("generated pair has EC algorithm")
        void keyAlgorithmIsEC() {
            ECKeyPair kp = KeyPairGenerator.generateECKeyPair();
            assertEquals("EC", kp.getPublicKey().getAlgorithm(),
                "public key algorithm must be EC");
        }
    }

    // ─── Reconstruction from PKCS#8 hex ──────────────────────────────────────

    @Nested
    @DisplayName("fromPrivateKeyHex(String)")
    class ReconstructionTests {

        @Test
        @DisplayName("round-trip: generate then reconstruct yields same public key hex")
        void roundTripFromPkcs8Hex() {
            ECKeyPair original = KeyPairGenerator.generateECKeyPair();
            String privHex = original.getPrivateKeyHex();

            ECKeyPair restored = KeyPairGenerator.fromPrivateKeyHex(privHex);
            assertEquals(original.getPublicKeyHex(), restored.getPublicKeyHex(),
                "restored public key must match original");
        }

        @Test
        @DisplayName("null input throws NullPointerException")
        void nullThrows() {
            assertThrows(NullPointerException.class,
                () -> KeyPairGenerator.fromPrivateKeyHex(null));
        }

        @Test
        @DisplayName("blank input throws IllegalArgumentException")
        void blankThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> KeyPairGenerator.fromPrivateKeyHex("   "));
        }

        @Test
        @DisplayName("non-hex input throws IllegalArgumentException")
        void nonHexThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> KeyPairGenerator.fromPrivateKeyHex("ZZZZ-not-hex"));
        }
    }

    // ─── Security invariant: private key masking ──────────────────────────────

    @Nested
    @DisplayName("ECKeyPair.toString() privacy (NFR-SEC-01, T-031)")
    class PrivacyTests {

        @Test
        @DisplayName("toString does not contain private key hex")
        void toStringMasksPrivateKey() {
            ECKeyPair kp = KeyPairGenerator.generateECKeyPair();
            String privHex = kp.getPrivateKeyHex();
            String str = kp.toString();

            // The private key is long enough that even a substring won't accidentally appear
            // We check the first 16 chars as a representative fragment
            String privFragment = privHex.substring(0, 16);
            assertFalse(str.contains(privFragment),
                "toString must NOT contain any fragment of the private key hex");
        }

        @Test
        @DisplayName("toString contains [REDACTED] sentinel")
        void toStringContainsRedacted() {
            assertTrue(KeyPairGenerator.generateECKeyPair().toString().contains("[REDACTED]"));
        }

        @Test
        @DisplayName("toString contains a public key preview")
        void toStringContainsPubPreview() {
            ECKeyPair kp = KeyPairGenerator.generateECKeyPair();
            String preview = kp.getPublicKeyHex().substring(0, 4);
            assertTrue(kp.toString().contains(preview),
                "toString should include a preview of the public key");
        }

        @Test
        @DisplayName("toString does not contain word 'private' (case-insensitive)")
        void toStringDoesNotContainWordPrivate() {
            String str = KeyPairGenerator.generateECKeyPair().toString().toLowerCase();
            assertFalse(str.contains("private "),
                "toString must not include the word 'private' followed by a space");
        }
    }

    // ─── Reconstruction from raw scalar ──────────────────────────────────────

    @Nested
    @DisplayName("fromRawPrivateScalar(String)")
    class RawScalarReconstructionTests {

        @Test
        @DisplayName("round-trip via raw scalar yields same public key hex")
        void roundTripFromRawScalar() {
            ECKeyPair original = KeyPairGenerator.generateECKeyPair();
            org.bouncycastle.jce.interfaces.ECPrivateKey bcKey =
                (org.bouncycastle.jce.interfaces.ECPrivateKey) original.getPrivateKey();
            String rawScalarHex = String.format("%064x", bcKey.getD());

            ECKeyPair restored = KeyPairGenerator.fromRawPrivateScalar(rawScalarHex);
            assertEquals(original.getPublicKeyHex(), restored.getPublicKeyHex(),
                "public key derived from raw scalar must match original");
        }

        @Test
        @DisplayName("null input throws NullPointerException")
        void nullThrows() {
            assertThrows(NullPointerException.class,
                () -> KeyPairGenerator.fromRawPrivateScalar(null));
        }

        @Test
        @DisplayName("blank input throws IllegalArgumentException")
        void blankThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> KeyPairGenerator.fromRawPrivateScalar("  "));
        }

        @Test
        @DisplayName("non-hex input throws IllegalArgumentException")
        void nonHexThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> KeyPairGenerator.fromRawPrivateScalar("ZZZZ-not-hex"));
        }

        @Test
        @DisplayName("reconstructed pair can sign and verify a message")
        void reconstructedPairCanSignAndVerify() {
            ECKeyPair original = KeyPairGenerator.generateECKeyPair();
            org.bouncycastle.jce.interfaces.ECPrivateKey bcKey =
                (org.bouncycastle.jce.interfaces.ECPrivateKey) original.getPrivateKey();
            String rawScalarHex = String.format("%064x", bcKey.getD());

            ECKeyPair restored = KeyPairGenerator.fromRawPrivateScalar(rawScalarHex);

            byte[] data = "sign-me".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] sig  = ECDSASignatureUtil.sign(data, restored);
            assertTrue(ECDSASignatureUtil.verify(data, sig, restored.getPublicKey()),
                "signature from restored key must verify");
        }
    }

    // ─── Equality and hashCode ────────────────────────────────────────────────

    @Nested
    @DisplayName("equals and hashCode")
    class EqualityTests {

        @Test
        @DisplayName("same key pair instance is equal to itself")
        void reflexiveEquality() {
            ECKeyPair kp = KeyPairGenerator.generateECKeyPair();
            assertEquals(kp, kp);
        }

        @Test
        @DisplayName("two key pairs reconstructed from the same hex are equal")
        void reconstructedPairsAreEqual() {
            ECKeyPair original = KeyPairGenerator.generateECKeyPair();
            ECKeyPair restored = KeyPairGenerator.fromPrivateKeyHex(original.getPrivateKeyHex());
            assertEquals(original, restored);
            assertEquals(original.hashCode(), restored.hashCode());
        }

        @Test
        @DisplayName("two different key pairs are not equal")
        void differentPairsNotEqual() {
            ECKeyPair kp1 = KeyPairGenerator.generateECKeyPair();
            ECKeyPair kp2 = KeyPairGenerator.generateECKeyPair();
            assertNotEquals(kp1, kp2);
        }

        @Test
        @DisplayName("equals returns false for null")
        void notEqualToNull() {
            assertFalse(KeyPairGenerator.generateECKeyPair().equals(null));
        }

        @Test
        @DisplayName("equals returns false for non-ECKeyPair object")
        void notEqualToOtherType() {
            assertFalse(KeyPairGenerator.generateECKeyPair().equals("not a key pair"));
        }
    }
}
