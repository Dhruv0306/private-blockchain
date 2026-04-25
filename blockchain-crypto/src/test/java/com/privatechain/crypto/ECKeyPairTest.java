package com.privatechain.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.security.PrivateKey;
import java.security.PublicKey;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ECKeyPair} and {@link KeyPairGenerator}.
 */
@DisplayName("ECKeyPair and KeyPairGenerator")
class ECKeyPairTest {

    private ECKeyPair keyPair;

    @BeforeEach
    void setUp() {
        keyPair = KeyPairGenerator.generateECKeyPair();
    }

    // ── ECKeyPair construction ────────────────────────────────────────────────

    @Nested
    @DisplayName("ECKeyPair construction")
    class ConstructionTests {

        @Test
        @DisplayName("generated pair has non-null public and private keys")
        void nonNull() {
            assertNotNull(keyPair.publicKey());
            assertNotNull(keyPair.privateKey());
        }

        @Test
        @DisplayName("null publicKey throws IllegalArgumentException")
        void nullPublicKey() {
            final PrivateKey pk = keyPair.privateKey();
            assertThrows(IllegalArgumentException.class,
                () -> new ECKeyPair(null, pk));
        }

        @Test
        @DisplayName("null privateKey throws IllegalArgumentException")
        void nullPrivateKey() {
            final PublicKey pub = keyPair.publicKey();
            assertThrows(IllegalArgumentException.class,
                () -> new ECKeyPair(pub, null));
        }
    }

    // ── Key encoding ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("hex encoding")
    class HexEncodingTests {

        @Test
        @DisplayName("toPublicKeyHex returns non-empty lowercase hex")
        void publicKeyHex() {
            final String hex = keyPair.toPublicKeyHex();
            assertNotNull(hex);
            assertFalse(hex.isEmpty());
            assertTrue(hex.matches("[0-9a-f]+"),
                "Expected lowercase hex but got: " + hex);
        }

        @Test
        @DisplayName("toPrivateKeyHex returns non-empty lowercase hex")
        void privateKeyHex() {
            final String hex = keyPair.toPrivateKeyHex();
            assertNotNull(hex);
            assertFalse(hex.isEmpty());
            assertTrue(hex.matches("[0-9a-f]+"),
                "Expected lowercase hex but got: " + hex);
        }

        @Test
        @DisplayName("public key round-trips through hex")
        void publicKeyRoundTrip() {
            final String hex = keyPair.toPublicKeyHex();
            final ECKeyPair reconstructed =
                KeyPairGenerator.fromHex(hex, keyPair.toPrivateKeyHex());
            assertEquals(hex, reconstructed.toPublicKeyHex());
        }

        @Test
        @DisplayName("private key round-trips through hex")
        void privateKeyRoundTrip() {
            final String pubHex = keyPair.toPublicKeyHex();
            final String privHex = keyPair.toPrivateKeyHex();
            final ECKeyPair reconstructed = KeyPairGenerator.fromHex(pubHex, privHex);
            assertEquals(privHex, reconstructed.toPrivateKeyHex());
        }
    }

    // ── toString safety ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("toString() security")
    class ToStringTests {

        @Test
        @DisplayName("toString does not contain the private key hex")
        void doesNotExposePrivateKey() {
            final String str = keyPair.toString();
            final String privHex = keyPair.toPrivateKeyHex();
            // The full private key must never appear in toString
            assertFalse(str.contains(privHex),
                "toString() must not expose the private key");
        }

        @Test
        @DisplayName("toString contains 'ECKeyPair'")
        void containsClassName() {
            assertTrue(keyPair.toString().startsWith("ECKeyPair["));
        }

        @Test
        @DisplayName("toString contains 'pubKey'")
        void containsPubKeyLabel() {
            assertTrue(keyPair.toString().contains("pubKey="));
        }
    }

    // ── KeyPairGenerator ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("KeyPairGenerator")
    class GeneratorTests {

        @RepeatedTest(5)
        @DisplayName("each generated pair has unique keys")
        void uniqueKeys() {
            final ECKeyPair other = KeyPairGenerator.generateECKeyPair();
            assertNotEquals(keyPair.toPublicKeyHex(), other.toPublicKeyHex(),
                "Two generated key pairs must not share a public key");
            assertNotEquals(keyPair.toPrivateKeyHex(), other.toPrivateKeyHex(),
                "Two generated key pairs must not share a private key");
        }

        @Test
        @DisplayName("fromHex(null, valid) throws IllegalArgumentException")
        void fromHexNullPublic() {
            assertThrows(IllegalArgumentException.class,
                () -> KeyPairGenerator.fromHex(null, keyPair.toPrivateKeyHex()));
        }

        @Test
        @DisplayName("fromHex(valid, null) throws IllegalArgumentException")
        void fromHexNullPrivate() {
            assertThrows(IllegalArgumentException.class,
                () -> KeyPairGenerator.fromHex(keyPair.toPublicKeyHex(), null));
        }

        @Test
        @DisplayName("fromHex with garbage hex throws CryptoException")
        void fromHexGarbage() {
            assertThrows(CryptoException.class,
                () -> KeyPairGenerator.fromHex("deadbeef", "cafebabe"));
        }

        @Test
        @DisplayName("publicKeyFromHex reconstructs correctly")
        void publicKeyFromHex() {
            final String hex = keyPair.toPublicKeyHex();
            final var publicKey = assertDoesNotThrow(
                () -> KeyPairGenerator.publicKeyFromHex(hex)
            );
            assertEquals(hex, HashUtil.bytesToHex(publicKey.getEncoded()));
        }

        @Test
        @DisplayName("publicKeyFromHex(null) throws IllegalArgumentException")
        void publicKeyFromHexNull() {
            assertThrows(IllegalArgumentException.class,
                () -> KeyPairGenerator.publicKeyFromHex(null));
        }

        @Test
        @DisplayName("algorithm is EC / secp256k1")
        void keyAlgorithm() {
            assertEquals("EC", keyPair.publicKey().getAlgorithm());
            assertEquals("EC", keyPair.privateKey().getAlgorithm());
        }
    }
}
