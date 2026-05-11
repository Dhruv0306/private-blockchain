package com.privatechain.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Utility class providing cryptographic hash functions used throughout the blockchain.
 *
 * <p>All methods return lowercase hexadecimal strings. BouncyCastle is registered
 * lazily on first use so that the JVM does not pay the registration cost when the
 * class is loaded but no hash is computed (FR-CRYPTO-01 – FR-CRYPTO-03).</p>
 *
 * <h2>Supported algorithms</h2>
 * <ul>
 *   <li>{@link #sha256(String)} / {@link #sha256(byte[])} — NIST SHA-256</li>
 *   <li>{@link #sha3_256(String)} / {@link #sha3_256(byte[])} — Keccak / NIST SHA-3-256</li>
 *   <li>{@link #doubleSha256(String)} / {@link #doubleSha256(byte[])} — SHA-256(SHA-256(x)),
 *       the double-hash used in Bitcoin-style block and transaction IDs</li>
 * </ul>
 *
 * <h2>Thread safety</h2>
 * All methods create fresh {@link MessageDigest} instances for each call so they
 * are safe to use from multiple threads simultaneously.
 *
 * <pre>{@code
 * String blockHash = HashUtil.sha256("index|prevHash|merkleRoot|timestamp");
 * String txId      = HashUtil.doubleSha256(rawBytes);
 * }</pre>
 *
 * @see ECDSASignatureUtil
 * @see MerkleTree
 * @since 1.0.0
 */
public final class HashUtil {

    // ─── Algorithm constants ──────────────────────────────────────────────────

    /** JCA algorithm name for SHA-256. */
    private static final String SHA_256 = "SHA-256";

    /**
     * JCA algorithm name for SHA-3-256 as provided by BouncyCastle.
     * The standard JDK 17+ also has "SHA3-256"; we use the BC name for consistency.
     */
    private static final String SHA3_256 = "SHA3-256";

    /** BouncyCastle JCA provider name. */
    private static final String BC_PROVIDER = "BC";

    // ─── Static initializer ───────────────────────────────────────────────────

    static {
        // Register BouncyCastle only if it is not already present.
        // Using insertProviderAt(1) would take priority over JDK providers;
        // addProvider appends it, which is sufficient for our needs.
        if (Security.getProvider(BC_PROVIDER) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * Utility class — no instances.
     */
    private HashUtil() {
        throw new UnsupportedOperationException("HashUtil is a utility class");
    }

    // ─── SHA-256 ──────────────────────────────────────────────────────────────

    /**
     * Computes the SHA-256 hash of a UTF-8–encoded string.
     *
     * @param input the string to hash (non-null)
     * @return lowercase 64-character hex string
     * @throws NullPointerException if input is null
     */
    public static String sha256(String input) {
        Objects.requireNonNull(input, "input must not be null");
        return sha256(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Computes the SHA-256 hash of a byte array.
     *
     * @param data the data to hash (non-null)
     * @return lowercase 64-character hex string
     * @throws NullPointerException if data is null
     */
    public static String sha256(byte[] data) {
        Objects.requireNonNull(data, "data must not be null");
        return HexFormat.of().formatHex(digest(SHA_256, data));
    }

    // ─── SHA-3-256 ────────────────────────────────────────────────────────────

    /**
     * Computes the SHA-3-256 hash of a UTF-8–encoded string.
     *
     * <p>SHA-3-256 (Keccak) is used in Ethereum-compatible address derivation
     * and as an alternative to SHA-256 in hash-proof generation.</p>
     *
     * @param input the string to hash (non-null)
     * @return lowercase 64-character hex string
     * @throws NullPointerException if input is null
     */
    public static String sha3_256(String input) {
        Objects.requireNonNull(input, "input must not be null");
        return sha3_256(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Computes the SHA-3-256 hash of a byte array.
     *
     * @param data the data to hash (non-null)
     * @return lowercase 64-character hex string
     * @throws NullPointerException if data is null
     */
    public static String sha3_256(byte[] data) {
        Objects.requireNonNull(data, "data must not be null");
        return HexFormat.of().formatHex(digestBc(SHA3_256, data));
    }

    // ─── Double-SHA-256 ───────────────────────────────────────────────────────

    /**
     * Computes SHA-256(SHA-256(input)) on a UTF-8–encoded string.
     *
     * <p>Double-hashing is used in Bitcoin-style transaction and block IDs
     * to provide additional collision resistance.</p>
     *
     * @param input the string to hash (non-null)
     * @return lowercase 64-character hex string
     * @throws NullPointerException if input is null
     */
    public static String doubleSha256(String input) {
        Objects.requireNonNull(input, "input must not be null");
        return doubleSha256(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Computes SHA-256(SHA-256(data)) on a byte array.
     *
     * @param data the data to hash (non-null)
     * @return lowercase 64-character hex string
     * @throws NullPointerException if data is null
     */
    public static String doubleSha256(byte[] data) {
        Objects.requireNonNull(data, "data must not be null");
        // First pass
        byte[] firstPass = digest(SHA_256, data);
        // Second pass over the raw bytes of the first hash
        byte[] secondPass = digest(SHA_256, firstPass);
        return HexFormat.of().formatHex(secondPass);
    }

    // ─── Raw byte helpers (package-private for use by MerkleTree etc.) ────────

    /**
     * Computes the raw SHA-256 digest bytes of the given data.
     *
     * <p>Package-private so that {@link MerkleTree} and other crypto utilities
     * can avoid the hex-encoding overhead when chaining hash operations.</p>
     *
     * @param data input bytes (non-null)
     * @return 32-byte SHA-256 digest
     */
    static byte[] sha256Bytes(byte[] data) {
        return digest(SHA_256, data);
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    /**
     * Creates a fresh JDK {@link MessageDigest} and digests the data.
     *
     * @param algorithm JCA algorithm name (e.g., "SHA-256")
     * @param data      input bytes
     * @return digest bytes
     */
    private static byte[] digest(String algorithm, byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            return md.digest(data);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JDK spec — this is truly unreachable
            throw new IllegalStateException("Hash algorithm unavailable: " + algorithm, e);
        }
    }

    /**
     * Creates a fresh BouncyCastle {@link MessageDigest} and digests the data.
     *
     * <p>Used for algorithms that may not be present in the default JDK provider
     * on all supported JVM versions (e.g., SHA3-256 on JDK 8).</p>
     *
     * @param algorithm BouncyCastle algorithm name (e.g., "SHA3-256")
     * @param data      input bytes
     * @return digest bytes
     */
    private static byte[] digestBc(String algorithm, byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm, BC_PROVIDER);
            return md.digest(data);
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new IllegalStateException(
                "BouncyCastle algorithm unavailable: " + algorithm, e);
        }
    }
}
