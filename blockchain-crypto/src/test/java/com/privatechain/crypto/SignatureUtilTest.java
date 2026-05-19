package com.privatechain.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ECDSASignatureUtil} covering signing, verification, and
 * the acceptance criterion that a tampered signature is rejected (AC-05).
 *
 * <p>Tests use {@link KeyPairGenerator} to create fresh keys for each test class
 * to keep tests independent and determinism-free.</p>
 */
@DisplayName("ECDSASignatureUtil")
class SignatureUtilTest {

    private ECKeyPair keyPair;
    private byte[] sampleData;

    @BeforeEach
    void setUp() {
        keyPair = KeyPairGenerator.generateECKeyPair();
        sampleData = "hello blockchain".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    // ─── Signing ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("sign(byte[], PrivateKey)")
    class SigningTests {

        @Test
        @DisplayName("sign returns non-null, non-empty byte array")
        void signReturnsNonEmptyBytes() {
            byte[] sig = ECDSASignatureUtil.sign(sampleData, keyPair.getPrivateKey());
            assertNotNull(sig);
            assertTrue(sig.length > 0, "DER-encoded signature must be non-empty");
        }

        @Test
        @DisplayName("sign(data, ECKeyPair) is equivalent to sign(data, privateKey)")
        void keyPairOverloadIsEquivalent() {
            // Both calls will produce different DER encodings due to ECDSA nonce randomness,
            // but both should verify correctly
            byte[] sig1 = ECDSASignatureUtil.sign(sampleData, keyPair);
            boolean verified = ECDSASignatureUtil.verify(sampleData, sig1, keyPair.getPublicKey());
            assertTrue(verified);
        }

        @Test
        @DisplayName("null data throws NullPointerException")
        void nullDataThrows() {
            assertThrows(NullPointerException.class,
                () -> ECDSASignatureUtil.sign(null, keyPair.getPrivateKey()));
        }

        @Test
        @DisplayName("null private key throws NullPointerException")
        void nullPrivateKeyThrows() {
            assertThrows(NullPointerException.class,
                () -> ECDSASignatureUtil.sign(sampleData, (java.security.PrivateKey) null));
        }

        @Test
        @DisplayName("empty data can be signed without error")
        void emptyDataSignable() {
            byte[] sig = ECDSASignatureUtil.sign(new byte[0], keyPair.getPrivateKey());
            assertNotNull(sig);
            assertTrue(sig.length > 0);
        }
    }

    // ─── Verification — happy path ────────────────────────────────────────────

    @Nested
    @DisplayName("verify — valid signature")
    class ValidVerificationTests {

        @Test
        @DisplayName("a freshly signed value verifies correctly")
        void freshSignatureVerifies() {
            byte[] sig = ECDSASignatureUtil.sign(sampleData, keyPair.getPrivateKey());
            assertTrue(ECDSASignatureUtil.verify(sampleData, sig, keyPair.getPublicKey()),
                "freshly signed data must verify");
        }

        @Test
        @DisplayName("verify with ECKeyPair overload succeeds")
        void keyPairVerifyOverload() {
            byte[] sig = ECDSASignatureUtil.sign(sampleData, keyPair);
            assertTrue(ECDSASignatureUtil.verify(sampleData, sig, keyPair));
        }

        @Test
        @DisplayName("large payload signs and verifies correctly")
        void largePayload() {
            byte[] largeData = new byte[4096];
            java.util.Arrays.fill(largeData, (byte) 0xAB);
            byte[] sig = ECDSASignatureUtil.sign(largeData, keyPair.getPrivateKey());
            assertTrue(ECDSASignatureUtil.verify(largeData, sig, keyPair.getPublicKey()));
        }
    }

    // ─── Verification — tampered data (AC-05) ────────────────────────────────

    @Nested
    @DisplayName("verify — tampered data (AC-05)")
    class TamperTests {

        @Test
        @DisplayName("tampered data causes signature verification to fail (AC-05)")
        void tamperedDataFails() {
            byte[] sig = ECDSASignatureUtil.sign(sampleData, keyPair.getPrivateKey());

            // Flip one bit in the data
            byte[] tampered = sampleData.clone();
            tampered[0] ^= 0x01;

            assertFalse(ECDSASignatureUtil.verify(tampered, sig, keyPair.getPublicKey()),
                "tampered data must NOT verify (AC-05)");
        }

        @Test
        @DisplayName("tampered signature is rejected")
        void tamperedSignatureFails() {
            byte[] sig = ECDSASignatureUtil.sign(sampleData, keyPair.getPrivateKey());

            // Corrupt the signature
            byte[] tampered = sig.clone();
            tampered[tampered.length / 2] ^= (byte) 0xFF;

            assertFalse(ECDSASignatureUtil.verify(sampleData, tampered, keyPair.getPublicKey()),
                "corrupted signature bytes must not verify");
        }

        @Test
        @DisplayName("signature created by one key does not verify under a different key")
        void wrongKeyFails() {
            ECKeyPair otherPair = KeyPairGenerator.generateECKeyPair();
            byte[] sig = ECDSASignatureUtil.sign(sampleData, keyPair.getPrivateKey());

            assertFalse(ECDSASignatureUtil.verify(sampleData, sig, otherPair.getPublicKey()),
                "signature must not verify under a different public key");
        }

        @Test
        @DisplayName("empty signature bytes returns false")
        void emptySignatureFails() {
            assertFalse(ECDSASignatureUtil.verify(sampleData, new byte[0], keyPair.getPublicKey()),
                "empty signature must fail verification");
        }
    }

    // ─── Null-argument guards ─────────────────────────────────────────────────

    @Nested
    @DisplayName("null argument guards")
    class NullGuardTests {

        @Test
        @DisplayName("null data in verify throws NullPointerException")
        void nullDataThrows() {
            byte[] sig = ECDSASignatureUtil.sign(sampleData, keyPair.getPrivateKey());
            assertThrows(NullPointerException.class,
                () -> ECDSASignatureUtil.verify(null, sig, keyPair.getPublicKey()));
        }

        @Test
        @DisplayName("null signature in verify throws NullPointerException")
        void nullSignatureThrows() {
            assertThrows(NullPointerException.class,
                () -> ECDSASignatureUtil.verify(sampleData, null, keyPair.getPublicKey()));
        }

        @Test
        @DisplayName("null public key in verify throws NullPointerException")
        void nullPublicKeyThrows() {
            byte[] sig = ECDSASignatureUtil.sign(sampleData, keyPair.getPrivateKey());
            assertThrows(NullPointerException.class,
                () -> ECDSASignatureUtil.verify(sampleData, sig, (java.security.PublicKey) null));
        }

        @Test
        @DisplayName("null ECKeyPair in sign throws NullPointerException")
        void nullKeyPairSignThrows() {
            assertThrows(NullPointerException.class,
                () -> ECDSASignatureUtil.sign(sampleData, (ECKeyPair) null));
        }

        @Test
        @DisplayName("null ECKeyPair in verify throws NullPointerException")
        void nullKeyPairVerifyThrows() {
            byte[] sig = ECDSASignatureUtil.sign(sampleData, keyPair.getPrivateKey());
            assertThrows(NullPointerException.class,
                () -> ECDSASignatureUtil.verify(sampleData, sig, (ECKeyPair) null));
        }
    }
}
