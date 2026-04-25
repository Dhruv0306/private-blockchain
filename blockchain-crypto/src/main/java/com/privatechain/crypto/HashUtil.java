package com.privatechain.crypto;

import org.bouncycastle.crypto.digests.RIPEMD160Digest;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Security;

/**
 * Cryptographic hashing utilities for the private-blockchain library.
 *
 * <p>All methods are static. This class cannot be instantiated.
 *
 * <p>Supported algorithms:
 * <ul>
 *   <li>SHA-256 — block hash, transaction ID derivation</li>
 *   <li>SHA3-256 — alternative hash, future-proof option</li>
 *   <li>Double-SHA-256 — SHA-256(SHA-256(input)), used by PoW mining</li>
 *   <li>RIPEMD-160 — address derivation step after SHA-256</li>
 * </ul>
 *
 * <p>All String inputs are encoded as UTF-8 before hashing.
 * All outputs are lowercase hex strings (no separators).
 *
 * <p>BouncyCastle is registered as a JCE security provider on class load.
 * This is idempotent — if already registered it is a no-op.
 *
 * @see ECKeyPair
 * @see AddressUtil
 */
public final class HashUtil {

    private static final Logger LOG = LoggerFactory.getLogger(HashUtil.class);

    private static final String SHA_256 = "SHA-256";
    private static final String SHA3_256 = "SHA3-256";
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
            LOG.debug("BouncyCastle provider registered by HashUtil");
        }
    }

    /** Utility class — no instances. */
    private HashUtil() {
        throw new UnsupportedOperationException("HashUtil is a utility class");
    }

    /**
     * Computes the SHA-256 hash of the given UTF-8 string.
     *
     * @param input the string to hash; must not be {@code null}
     * @return lowercase hex-encoded SHA-256 digest (64 characters)
     * @throws IllegalArgumentException if {@code input} is {@code null}
     */
    public static String sha256(final String input) {
        requireNonNull(input, "input");
        return bytesToHex(digest(SHA_256, input.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Computes the SHA-256 hash of the given raw bytes.
     *
     * @param input the bytes to hash; must not be {@code null}
     * @return lowercase hex-encoded SHA-256 digest (64 characters)
     * @throws IllegalArgumentException if {@code input} is {@code null}
     */
    public static String sha256(final byte[] input) {
        requireNonNull(input, "input");
        return bytesToHex(digest(SHA_256, input));
    }

    /**
     * Computes the SHA3-256 hash of the given UTF-8 string.
     *
     * @param input the string to hash; must not be {@code null}
     * @return lowercase hex-encoded SHA3-256 digest (64 characters)
     * @throws IllegalArgumentException if {@code input} is {@code null}
     */
    public static String sha3_256(final String input) {
        requireNonNull(input, "input");
        return bytesToHex(digest(SHA3_256, input.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Computes SHA-256(SHA-256(input)) — double hash.
     *
     * <p>Used by Proof-of-Work mining to increase collision resistance.
     *
     * @param input the string to hash; must not be {@code null}
     * @return lowercase hex-encoded double-SHA-256 digest (64 characters)
     * @throws IllegalArgumentException if {@code input} is {@code null}
     */
    public static String doubleSha256(final String input) {
        requireNonNull(input, "input");
        final byte[] firstPass = digest(SHA_256, input.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(digest(SHA_256, firstPass));
    }

    /**
     * Computes the RIPEMD-160 hash of the given bytes.
     *
     * <p>Used in address derivation: {@code RIPEMD160(SHA256(publicKeyBytes))}.
     * Requires BouncyCastle — registered on class load.
     *
     * @param input the bytes to hash; must not be {@code null}
     * @return 20-byte RIPEMD-160 digest (raw bytes, not hex)
     * @throws IllegalArgumentException if {@code input} is {@code null}
     */
    public static byte[] ripemd160(final byte[] input) {
        requireNonNull(input, "input");
        final RIPEMD160Digest digest = new RIPEMD160Digest();
        digest.update(input, 0, input.length);
        final byte[] result = new byte[digest.getDigestSize()];
        digest.doFinal(result, 0);
        return result;
    }

    /**
     * Converts a byte array to a lowercase hexadecimal string.
     *
     * @param bytes the bytes to convert; must not be {@code null}
     * @return lowercase hex string, 2 characters per byte
     * @throws IllegalArgumentException if {@code bytes} is {@code null}
     */
    public static String bytesToHex(final byte[] bytes) {
        requireNonNull(bytes, "bytes");
        final char[] hex = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            final int value = bytes[i] & 0xFF;
            hex[i * 2] = HEX_CHARS[value >>> 4];
            hex[i * 2 + 1] = HEX_CHARS[value & 0x0F];
        }
        return new String(hex);
    }

    /**
     * Converts a lowercase hexadecimal string to a byte array.
     *
     * @param hex the hex string to decode; must not be {@code null}, length must be even
     * @return decoded byte array
     * @throws IllegalArgumentException if {@code hex} is {@code null} or has odd length
     */
    public static byte[] hexToBytes(final String hex) {
        requireNonNull(hex, "hex");
        if (hex.length() % 2 != 0) {
            throw new IllegalArgumentException(
                "Hex string must have even length, got: " + hex.length()
            );
        }
        final byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            result[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return result;
    }

    // ── internal helpers ──────────────────────────────────────────────────────

    private static byte[] digest(final String algorithm, final byte[] input) {
        try {
            final MessageDigest md = MessageDigest.getInstance(algorithm);
            return md.digest(input);
        } catch (NoSuchAlgorithmException e) {
            // Both SHA-256 and SHA3-256 are guaranteed by the JDK spec (Java 9+)
            // and BouncyCastle. This branch is unreachable in normal operation.
            throw new IllegalStateException(
                "Hash algorithm not available: " + algorithm, e
            );
        }
    }

    private static void requireNonNull(final Object value, final String name) {
        if (value == null) {
            throw new IllegalArgumentException("'" + name + "' must not be null");
        }
    }
}
