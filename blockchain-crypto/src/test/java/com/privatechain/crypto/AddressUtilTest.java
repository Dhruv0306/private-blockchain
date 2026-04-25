package com.privatechain.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link AddressUtil}.
 */
@DisplayName("AddressUtil")
class AddressUtilTest {

    private ECKeyPair keyPair;

    @BeforeEach
    void setUp() {
        keyPair = KeyPairGenerator.generateECKeyPair();
    }

    // ── deriveAddress(PublicKey) ──────────────────────────────────────────────

    @Nested
    @DisplayName("deriveAddress(PublicKey)")
    class DeriveAddressFromKeyTests {

        @Test
        @DisplayName("produces a non-null result")
        void nonNull() {
            assertNotNull(AddressUtil.deriveAddress(keyPair.publicKey()));
        }

        @Test
        @DisplayName("address is exactly 40 hex characters (20 bytes RIPEMD-160)")
        void correctLength() {
            final String address = AddressUtil.deriveAddress(keyPair.publicKey());
            assertEquals(AddressUtil.ADDRESS_HEX_LENGTH, address.length(),
                "Address should be 40 chars but was: " + address.length());
        }

        @Test
        @DisplayName("address is lowercase hex")
        void lowercaseHex() {
            final String address = AddressUtil.deriveAddress(keyPair.publicKey());
            assertTrue(address.matches("[0-9a-f]{40}"),
                "Address should be lowercase hex: " + address);
        }

        @Test
        @DisplayName("same key always yields same address (deterministic)")
        void deterministic() {
            final String addr1 = AddressUtil.deriveAddress(keyPair.publicKey());
            final String addr2 = AddressUtil.deriveAddress(keyPair.publicKey());
            assertEquals(addr1, addr2);
        }

        @Test
        @DisplayName("different keys yield different addresses")
        void differentKeysYieldDifferentAddresses() {
            final ECKeyPair other = KeyPairGenerator.generateECKeyPair();
            final String addr1 = AddressUtil.deriveAddress(keyPair.publicKey());
            final String addr2 = AddressUtil.deriveAddress(other.publicKey());
            assertNotEquals(addr1, addr2,
                "Different key pairs should produce different addresses");
        }

        @Test
        @DisplayName("null publicKey throws IllegalArgumentException")
        void nullThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> AddressUtil.deriveAddress((java.security.PublicKey) null));
        }
    }

    // ── deriveAddress(String hex) ─────────────────────────────────────────────

    @Nested
    @DisplayName("deriveAddress(String publicKeyHex)")
    class DeriveAddressFromHexTests {

        @Test
        @DisplayName("produces same result as deriveAddress(PublicKey)")
        void matchesPublicKeyOverload() {
            final String hexAddr     = AddressUtil.deriveAddress(keyPair.toPublicKeyHex());
            final String directAddr  = AddressUtil.deriveAddress(keyPair.publicKey());
            assertEquals(directAddr, hexAddr);
        }

        @Test
        @DisplayName("null hex throws IllegalArgumentException")
        void nullThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> AddressUtil.deriveAddress((String) null));
        }

        @Test
        @DisplayName("invalid hex throws CryptoException")
        void invalidHexThrows() {
            assertThrows(CryptoException.class,
                () -> AddressUtil.deriveAddress("notvalidhex"));
        }
    }

    // ── isValidAddress ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isValidAddress()")
    class IsValidAddressTests {

        @Test
        @DisplayName("address derived from a real key is valid")
        void derivedAddressIsValid() {
            final String address = AddressUtil.deriveAddress(keyPair.publicKey());
            assertTrue(AddressUtil.isValidAddress(address));
        }

        @Test
        @DisplayName("40 lowercase hex chars is valid")
        void fortyLowercaseHexIsValid() {
            assertTrue(AddressUtil.isValidAddress("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2"));
        }

        @Test
        @DisplayName("null is invalid")
        void nullIsInvalid() {
            assertFalse(AddressUtil.isValidAddress(null));
        }

        @Test
        @DisplayName("39 chars is invalid (too short)")
        void tooShortIsInvalid() {
            assertFalse(AddressUtil.isValidAddress("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1"));
        }

        @Test
        @DisplayName("41 chars is invalid (too long)")
        void tooLongIsInvalid() {
            assertFalse(AddressUtil.isValidAddress("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c"));
        }

        @Test
        @DisplayName("uppercase hex is invalid")
        void uppercaseIsInvalid() {
            assertFalse(AddressUtil.isValidAddress("A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4E5F6A1B2"));
        }

        @Test
        @DisplayName("non-hex characters are invalid")
        void nonHexIsInvalid() {
            assertFalse(AddressUtil.isValidAddress("z1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2"));
        }

        @Test
        @DisplayName("empty string is invalid")
        void emptyIsInvalid() {
            assertFalse(AddressUtil.isValidAddress(""));
        }
    }
}
