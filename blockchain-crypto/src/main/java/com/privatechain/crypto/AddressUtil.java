package com.privatechain.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.*;
import java.util.Arrays;
import java.util.Objects;

/**
 * Utility class for deriving a blockchain address from an EC public key.
 *
 * <p>The derivation pipeline follows the Bitcoin / Web3 convention
 * (FR-CRYPTO-09):</p>
 * <ol>
 *   <li>SHA-256 of the DER-encoded public key bytes</li>
 *   <li>RIPEMD-160 of the SHA-256 result (reduces size from 32 → 20 bytes)</li>
 *   <li>Base58Check encoding with a configurable version byte
 *       (default {@code 0x00} — main net Pay-to-Public-Key-Hash)</li>
 * </ol>
 *
 * <p>The result is a 25–34 character, human-readable, typo-resistant address
 * that can be verified for internal checksum consistency.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * ECKeyPair keyPair = KeyPairGenerator.generateECKeyPair();
 * String address    = AddressUtil.deriveAddress(keyPair.getPublicKey());
 * // e.g., "1A1zP1eP5QGefi2DMPTfTL5SLmv7Divf..."
 * }</pre>
 *
 * @see KeyPairGenerator
 * @see ECKeyPair
 * @since 1.0.0
 */
public final class AddressUtil {

    // ─── Algorithm constants ──────────────────────────────────────────────────

    /**
     * RIPEMD-160 algorithm name under the BouncyCastle provider.
     */
    private static final String RIPEMD_160 = "RIPEMD160";

    /**
     * BouncyCastle provider name.
     */
    private static final String BC_PROVIDER = "BC";

    /**
     * Number of leading bytes used for the Base58Check checksum.
     * Bitcoin convention: last 4 bytes of double-SHA-256.
     */
    private static final int CHECKSUM_LENGTH = 4;

    /**
     * Base58 character alphabet (Bitcoin convention — no 0, O, I, l to avoid confusion).
     */
    private static final char[] BASE58_ALPHABET =
        "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();

    /**
     * Default version byte for main net P2PKH addresses ({@code 0x00}).
     */
    private static final byte VERSION_MAINNET = 0x00;

    static {
        if (Security.getProvider(BC_PROVIDER) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * Utility class — no instances.
     */
    private AddressUtil() {
        throw new UnsupportedOperationException("AddressUtil is a utility class");
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Derives a Base58Check-encoded blockchain address from a secp256k1 public key.
     *
     * <p>The pipeline is: SHA-256 → RIPEMD-160 → version byte prefix → Base58Check.</p>
     *
     * @param publicKey the EC public key to derive an address from (non-null)
     * @return a Base58Check address string (25–34 characters, main net version byte)
     * @throws NullPointerException  if publicKey is null
     * @throws IllegalStateException if the BouncyCastle provider is missing
     */
    public static String deriveAddress(PublicKey publicKey) {
        Objects.requireNonNull(publicKey, "publicKey must not be null");
        return deriveAddress(publicKey.getEncoded(), VERSION_MAINNET);
    }

    /**
     * Derives a Base58Check-encoded address from raw public-key bytes with a
     * custom version byte.
     *
     * <p>Pass a custom version byte (e.g., {@code 0x6f} for Bitcoin testnet)
     * to produce network-specific addresses.</p>
     *
     * @param publicKeyBytes DER-encoded public key bytes (non-null, non-empty)
     * @param versionByte    single-byte network identifier prepended to the hash
     * @return a Base58Check address string
     * @throws NullPointerException     if publicKeyBytes is null
     * @throws IllegalArgumentException if publicKeyBytes is empty
     * @throws IllegalStateException    if the BouncyCastle provider is missing
     */
    public static String deriveAddress(byte[] publicKeyBytes, byte versionByte) {
        Objects.requireNonNull(publicKeyBytes, "publicKeyBytes must not be null");
        if (publicKeyBytes.length == 0) {
            throw new IllegalArgumentException("publicKeyBytes must not be empty");
        }

        // Step 1: SHA-256 of the public key bytes
        byte[] sha256Hash = HashUtil.sha256Bytes(publicKeyBytes);

        // Step 2: RIPEMD-160 of the SHA-256 result (20 bytes)
        byte[] ripemd160Hash = ripemd160(sha256Hash);

        // Step 3: Prepend version byte → 21 bytes total
        byte[] versionedHash = new byte[1 + ripemd160Hash.length];
        versionedHash[0] = versionByte;
        System.arraycopy(ripemd160Hash, 0, versionedHash, 1, ripemd160Hash.length);

        // Step 4: Compute checksum (first 4 bytes of double-SHA-256)
        byte[] checksum = computeChecksum(versionedHash);

        // Step 5: Concatenate versioned hash + checksum (25 bytes)
        byte[] addressBytes = Arrays.copyOf(versionedHash, versionedHash.length + CHECKSUM_LENGTH);
        System.arraycopy(checksum, 0, addressBytes, versionedHash.length, CHECKSUM_LENGTH);

        // Step 6: Base58 encode (no padding character at the end)
        return base58Encode(addressBytes);
    }

    /**
     * Verifies that a Base58Check-encoded address has a valid internal checksum.
     *
     * <p>This check does not verify that the address belongs to a known public key;
     * it only confirms that the address string was not corrupted in transit.</p>
     *
     * @param address the Base58Check-encoded address to verify (non-null, non-blank)
     * @return {@code true} if the embedded checksum is valid; {@code false} otherwise
     * @throws NullPointerException     if address is null
     * @throws IllegalArgumentException if address is blank
     */
    public static boolean isAddressValid(String address) {
        Objects.requireNonNull(address, "address must not be null");
        if (address.isBlank()) {
            throw new IllegalArgumentException("address must not be blank");
        }

        try {
            byte[] decoded = base58Decode(address);
            if (decoded.length < CHECKSUM_LENGTH + 1) {
                // Too short to hold version byte + any hash + checksum
                return false;
            }

            // Split payload and embedded checksum
            int payloadLength = decoded.length - CHECKSUM_LENGTH;
            byte[] payload = Arrays.copyOfRange(decoded, 0, payloadLength);
            byte[] checksum = Arrays.copyOfRange(decoded, payloadLength, decoded.length);

            // Recompute checksum from payload
            byte[] recomputed = computeChecksum(payload);
            return Arrays.equals(checksum, recomputed);

        } catch (IllegalArgumentException e) {
            // Base58 decoding failed — not a valid address
            return false;
        }
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    /**
     * Computes the RIPEMD-160 digest of the given data using the BouncyCastle provider.
     *
     * @param data input bytes
     * @return 20-byte RIPEMD-160 digest
     */
    private static byte[] ripemd160(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance(RIPEMD_160, BC_PROVIDER);
            return md.digest(data);
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new IllegalStateException(
                "RIPEMD-160 unavailable via BouncyCastle: " + e.getMessage(), e);
        }
    }

    /**
     * Computes the Base58Check checksum: the first {@value #CHECKSUM_LENGTH} bytes
     * of double-SHA-256.
     *
     * @param payload the data whose checksum is to be computed
     * @return 4-byte checksum array
     */
    private static byte[] computeChecksum(byte[] payload) {
        // doubleSha256 returns hex; we need the raw bytes
        byte[] firstHash = HashUtil.sha256Bytes(payload);
        byte[] secondHash = HashUtil.sha256Bytes(firstHash);
        return Arrays.copyOfRange(secondHash, 0, CHECKSUM_LENGTH);
    }

    /**
     * Encodes a byte array using the Base58 alphabet (no padding, no line-breaks).
     *
     * <p>Leading zero bytes in the input are represented as leading '1' characters
     * in the output (Bitcoin convention).</p>
     *
     * @param input bytes to encode
     * @return Base58-encoded string
     */
    private static String base58Encode(byte[] input) {
        // Count leading zero bytes
        int leadingZeros = 0;
        for (byte b : input) {
            if (b == 0) {
                leadingZeros++;
            } else {
                break;
            }
        }

        // Encode using big-integer division
        // Work with an unsigned representation
        java.math.BigInteger value = new java.math.BigInteger(1, input);
        java.math.BigInteger base = java.math.BigInteger.valueOf(58);

        StringBuilder encoded = new StringBuilder();
        while (value.compareTo(java.math.BigInteger.ZERO) > 0) {
            java.math.BigInteger[] divMod = value.divideAndRemainder(base);
            encoded.append(BASE58_ALPHABET[divMod[1].intValue()]);
            value = divMod[0];
        }

        // Prepend '1' for each leading zero byte
        encoded.append(String.valueOf(BASE58_ALPHABET[0]).repeat(Math.max(0, leadingZeros)));

        return encoded.reverse().toString();
    }

    /**
     * Decodes a Base58-encoded string back to raw bytes.
     *
     * @param input Base58-encoded string
     * @return decoded bytes
     * @throws IllegalArgumentException if a character is not in the Base58 alphabet
     */
    private static byte[] base58Decode(String input) {
        java.math.BigInteger result = java.math.BigInteger.ZERO;
        java.math.BigInteger base = java.math.BigInteger.valueOf(58);

        for (char c : input.toCharArray()) {
            int digit = charToBase58Digit(c);
            result = result.multiply(base).add(java.math.BigInteger.valueOf(digit));
        }

        // Count leading '1' characters → leading zero bytes
        int leadingZeros = 0;
        for (char c : input.toCharArray()) {
            if (c == '1') {
                leadingZeros++;
            } else {
                break;
            }
        }

        byte[] resultBytes = result.toByteArray();
        // BigInteger may prepend a sign byte; strip it
        int stripSignByte = (resultBytes.length > 1 && resultBytes[0] == 0) ? 1 : 0;

        byte[] decoded = new byte[leadingZeros + resultBytes.length - stripSignByte];
        System.arraycopy(resultBytes, stripSignByte, decoded, leadingZeros,
            resultBytes.length - stripSignByte);
        return decoded;
    }

    /**
     * Maps a Base58 character to its numeric value.
     *
     * @param c the character to look up
     * @return numeric value 0–57
     * @throws IllegalArgumentException if the character is not in the Base58 alphabet
     */
    private static int charToBase58Digit(char c) {
        for (int i = 0; i < BASE58_ALPHABET.length; i++) {
            if (BASE58_ALPHABET[i] == c) {
                return i;
            }
        }
        throw new IllegalArgumentException("Character '" + c + "' is not in the Base58 alphabet");
    }
}
