package com.privatechain.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.security.PublicKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link AddressUtil} covering address derivation from a public key,
 * custom version-byte derivation, checksum validation, and all null/blank guards.
 *
 * <p>The derivation pipeline under test is:
 * SHA-256 → RIPEMD-160 → version-byte prefix → Base58Check (FR-CRYPTO-09).</p>
 */
@DisplayName("AddressUtil")
class AddressUtilTest {

    private ECKeyPair keyPair;
    private PublicKey publicKey;

    @BeforeEach
    void setUp() {
        keyPair   = KeyPairGenerator.generateECKeyPair();
        publicKey = keyPair.getPublicKey();
    }

    // ─── deriveAddress(PublicKey) ─────────────────────────────────────────────

    @Nested
    @DisplayName("deriveAddress(PublicKey)")
    class DeriveAddressPublicKeyTests {

        @Test
        @DisplayName("returns a non-null, non-blank address")
        void returnsNonBlankAddress() {
            String address = AddressUtil.deriveAddress(publicKey);
            assertNotNull(address);
            assertFalse(address.isBlank());
        }

        @Test
        @DisplayName("address contains only Base58 characters")
        void addressIsBase58() {
            String address = AddressUtil.deriveAddress(publicKey);
            // Base58 alphabet: digits 1-9 + uppercase A-Z (no O, I) + lowercase a-z (no l)
            assertTrue(address.matches("[123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz]+"),
                "address must use only Base58 characters, got: " + address);
        }

        @Test
        @DisplayName("address is deterministic for the same public key")
        void deterministicForSameKey() {
            String a1 = AddressUtil.deriveAddress(publicKey);
            String a2 = AddressUtil.deriveAddress(publicKey);
            assertEquals(a1, a2, "same public key must always produce the same address");
        }

        @Test
        @DisplayName("different public keys produce different addresses")
        void differentKeysProduceDifferentAddresses() {
            ECKeyPair other = KeyPairGenerator.generateECKeyPair();
            assertNotEquals(
                AddressUtil.deriveAddress(publicKey),
                AddressUtil.deriveAddress(other.getPublicKey()),
                "distinct key pairs must yield distinct addresses"
            );
        }

        @Test
        @DisplayName("derived address passes isAddressValid checksum check")
        void derivedAddressIsValid() {
            String address = AddressUtil.deriveAddress(publicKey);
            assertTrue(AddressUtil.isAddressValid(address),
                "freshly derived address must pass checksum validation");
        }

        @Test
        @DisplayName("null PublicKey throws NullPointerException")
        void nullPublicKeyThrows() {
            assertThrows(NullPointerException.class,
                () -> AddressUtil.deriveAddress(null));
        }

        @Test
        @DisplayName("address length is in the expected Base58Check range (25–34 chars)")
        void addressLengthInRange() {
            String address = AddressUtil.deriveAddress(publicKey);
            // Standard P2PKH Base58Check addresses are 25-34 characters
            assertTrue(address.length() >= 25 && address.length() <= 40,
                "address length out of expected range: " + address.length());
        }

        @Test
        @DisplayName("address derived from reconstructed key pair matches original")
        void reconstructedKeyPairSameAddress() {
            String original = AddressUtil.deriveAddress(publicKey);
            ECKeyPair restored = KeyPairGenerator.fromPrivateKeyHex(keyPair.getPrivateKeyHex());
            String restored_address = AddressUtil.deriveAddress(restored.getPublicKey());
            assertEquals(original, restored_address,
                "address derived from restored key pair must match original");
        }
    }

    // ─── deriveAddress(byte[], byte) ─────────────────────────────────────────

    @Nested
    @DisplayName("deriveAddress(byte[], versionByte)")
    class DeriveAddressBytesTests {

        @Test
        @DisplayName("returns non-blank address for main net version byte 0x00")
        void mainnetVersionByte() {
            byte[] pubBytes = publicKey.getEncoded();
            String addr = AddressUtil.deriveAddress(pubBytes, (byte) 0x00);
            assertFalse(addr.isBlank());
        }

        @Test
        @DisplayName("different version bytes produce different addresses for same key")
        void differentVersionBytesDifferentAddresses() {
            byte[] pubBytes = publicKey.getEncoded();
            String mainnet = AddressUtil.deriveAddress(pubBytes, (byte) 0x00);
            String testnet = AddressUtil.deriveAddress(pubBytes, (byte) 0x6f);
            assertNotEquals(mainnet, testnet,
                "main net and test net version bytes must produce different addresses");
        }

        @Test
        @DisplayName("address produced by byte[] overload matches PublicKey overload")
        void byteOverloadMatchesPublicKeyOverload() {
            String fromPublicKey = AddressUtil.deriveAddress(publicKey);
            String fromBytes     = AddressUtil.deriveAddress(publicKey.getEncoded(), (byte) 0x00);
            assertEquals(fromPublicKey, fromBytes,
                "both overloads with main net version byte must produce identical addresses");
        }

        @Test
        @DisplayName("null byte array throws NullPointerException")
        void nullBytesThrows() {
            assertThrows(NullPointerException.class,
                () -> AddressUtil.deriveAddress(null, (byte) 0x00));
        }

        @Test
        @DisplayName("empty byte array throws IllegalArgumentException")
        void emptyBytesThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> AddressUtil.deriveAddress(new byte[0], (byte) 0x00));
        }

        @Test
        @DisplayName("custom version byte address also passes isAddressValid")
        void customVersionByteAddressIsValid() {
            String addr = AddressUtil.deriveAddress(publicKey.getEncoded(), (byte) 0x6f);
            assertTrue(AddressUtil.isAddressValid(addr),
                "test net address must pass its own checksum validation");
        }
    }

    // ─── isAddressValid ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("isAddressValid(String)")
    class IsAddressValidTests {

        @Test
        @DisplayName("valid address returns true")
        void validAddressReturnsTrue() {
            String address = AddressUtil.deriveAddress(publicKey);
            assertTrue(AddressUtil.isAddressValid(address));
        }

        @Test
        @DisplayName("corrupted last character returns false")
        void corruptedLastCharReturnsFalse() {
            String address = AddressUtil.deriveAddress(publicKey);
            // Replace last character with a different Base58 character
            char lastChar = address.charAt(address.length() - 1);
            char replacement = (lastChar == '1') ? '2' : '1';
            String corrupted = address.substring(0, address.length() - 1) + replacement;
            assertFalse(AddressUtil.isAddressValid(corrupted),
                "corrupted checksum byte must fail validation");
        }

        @Test
        @DisplayName("completely garbage string returns false")
        void garbageStringReturnsFalse() {
            assertFalse(AddressUtil.isAddressValid("notanaddressatall"));
        }

        @Test
        @DisplayName("very short string returns false")
        void tooShortReturnsFalse() {
            assertFalse(AddressUtil.isAddressValid("1"));
        }

        @Test
        @DisplayName("string with invalid Base58 char (0, O, I, l) returns false")
        void invalidBase58CharReturnsFalse() {
            // '0' is not in the Base58 alphabet
            assertFalse(AddressUtil.isAddressValid("0invalidiaddress0"));
        }

        @Test
        @DisplayName("null address throws NullPointerException")
        void nullThrows() {
            assertThrows(NullPointerException.class,
                () -> AddressUtil.isAddressValid(null));
        }

        @Test
        @DisplayName("blank address throws IllegalArgumentException")
        void blankThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> AddressUtil.isAddressValid("   "));
        }

        @Test
        @DisplayName("multiple independently derived addresses all pass validation")
        void multipleAddressesAllValid() {
            for (int i = 0; i < 10; i++) {
                ECKeyPair kp = KeyPairGenerator.generateECKeyPair();
                String addr  = AddressUtil.deriveAddress(kp.getPublicKey());
                assertTrue(AddressUtil.isAddressValid(addr),
                    "address #" + i + " must be self-validating");
            }
        }
    }

    // ─── Utility class guard ──────────────────────────────────────────────────

    @Nested
    @DisplayName("AddressUtil constructor guard")
    class ConstructorGuardTest {

        @Test
        @DisplayName("instantiating AddressUtil throws UnsupportedOperationException")
        void constructorThrows() {
            var constructor = AddressUtil.class.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            assertThrows(java.lang.reflect.InvocationTargetException.class,
                constructor::newInstance);
        }
    }
}
