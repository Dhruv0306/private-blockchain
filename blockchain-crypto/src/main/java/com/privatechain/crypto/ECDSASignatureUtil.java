package com.privatechain.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.SignatureException;
import java.util.Objects;

/**
 * Utility class for ECDSA signing and signature verification using the
 * <a href="https://en.bitcoin.it/wiki/Secp256k1">secp256k1</a> elliptic curve
 * and the SHA-256withECDSA algorithm.
 *
 * <p>All operations use BouncyCastle as the JCA provider because
 * secp256k1 is not available in the standard JDK provider on all JVM versions
 * (FR-CRYPTO-04, FR-CRYPTO-05).</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * ECKeyPair keyPair = KeyPairGenerator.generateECKeyPair();
 *
 * // Signing
 * byte[] data      = tx.toSignableBytes();
 * byte[] signature = ECDSASignatureUtil.sign(data, keyPair.getPrivateKey());
 *
 * // Verification
 * boolean valid = ECDSASignatureUtil.verify(data, signature, keyPair.getPublicKey());
 * }</pre>
 *
 * <h2>Thread safety</h2>
 * All methods create fresh {@link java.security.Signature} instances per call,
 * so they are safe to call concurrently from multiple threads.
 *
 * @see KeyPairGenerator
 * @see ECKeyPair
 * @since 1.0.0
 */
public final class ECDSASignatureUtil {

    /** JCA algorithm identifier for SHA-256 with ECDSA. */
    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";

    /** BouncyCastle JCA provider name. */
    private static final String BC_PROVIDER = "BC";

    static {
        if (Security.getProvider(BC_PROVIDER) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * Utility class — no instances.
     */
    private ECDSASignatureUtil() {
        throw new UnsupportedOperationException("ECDSASignatureUtil is a utility class");
    }

    // ─── Signing ──────────────────────────────────────────────────────────────

    /**
     * Signs a byte array with the given ECDSA private key using SHA-256withECDSA.
     *
     * <p>The returned DER-encoded signature may be passed to
     * {@link #verify(byte[], byte[], PublicKey)} to confirm the signer's identity.</p>
     *
     * @param data       the raw bytes to sign (non-null; may be empty but typically non-empty)
     * @param privateKey the signer's secp256k1 private key (non-null)
     * @return DER-encoded ECDSA signature bytes (typically 70–72 bytes)
     * @throws NullPointerException  if data or privateKey is null
     * @throws IllegalStateException if the BouncyCastle provider is unavailable or
     *                               the key is rejected by the JCA engine
     */
    public static byte[] sign(byte[] data, PrivateKey privateKey) {
        Objects.requireNonNull(data, "data must not be null");
        Objects.requireNonNull(privateKey, "privateKey must not be null");

        try {
            java.security.Signature sig =
                java.security.Signature.getInstance(SIGNATURE_ALGORITHM, BC_PROVIDER);
            sig.initSign(privateKey);
            sig.update(data);
            return sig.sign();

        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new IllegalStateException(
                "ECDSA signature algorithm unavailable: " + e.getMessage(), e);
        } catch (InvalidKeyException e) {
            throw new IllegalStateException(
                "Private key is invalid for ECDSA signing: " + e.getMessage(), e);
        } catch (SignatureException e) {
            throw new IllegalStateException(
                "ECDSA signing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience overload that accepts an {@link ECKeyPair} directly.
     *
     * @param data    the raw bytes to sign (non-null)
     * @param keyPair the key pair whose private key will be used (non-null)
     * @return DER-encoded ECDSA signature bytes
     * @throws NullPointerException  if data or keyPair is null
     * @throws IllegalStateException if signing fails
     */
    public static byte[] sign(byte[] data, ECKeyPair keyPair) {
        Objects.requireNonNull(keyPair, "keyPair must not be null");
        return sign(data, keyPair.getPrivateKey());
    }

    // ─── Verification ─────────────────────────────────────────────────────────

    /**
     * Verifies a DER-encoded ECDSA signature against raw data and a public key.
     *
     * <p>This method must be called before any transaction enters the mempool
     * (NFR-SEC-02). It returns {@code false} — rather than throwing — on any
     * verification failure so that callers can treat invalid signatures as normal
     * validation failures rather than exceptional errors.</p>
     *
     * @param data      the original data that was signed (non-null)
     * @param signature the DER-encoded ECDSA signature to verify (non-null)
     * @param publicKey the signer's secp256k1 public key (non-null)
     * @return {@code true} if the signature is valid; {@code false} otherwise
     * @throws NullPointerException  if any argument is null
     * @throws IllegalStateException if the BouncyCastle provider is unavailable
     */
    public static boolean verify(byte[] data, byte[] signature, PublicKey publicKey) {
        Objects.requireNonNull(data, "data must not be null");
        Objects.requireNonNull(signature, "signature must not be null");
        Objects.requireNonNull(publicKey, "publicKey must not be null");

        try {
            java.security.Signature sig =
                java.security.Signature.getInstance(SIGNATURE_ALGORITHM, BC_PROVIDER);
            sig.initVerify(publicKey);
            sig.update(data);
            return sig.verify(signature);

        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new IllegalStateException(
                "ECDSA signature algorithm unavailable: " + e.getMessage(), e);
        } catch (InvalidKeyException e) {
            // The public key is structurally invalid — treat as a failed verification
            return false;
        } catch (SignatureException e) {
            // Malformed DER bytes, wrong length, etc. — treat as failed verification
            return false;
        }
    }

    /**
     * Convenience overload that accepts an {@link ECKeyPair} for verification.
     *
     * @param data      the original data that was signed (non-null)
     * @param signature the DER-encoded ECDSA signature to verify (non-null)
     * @param keyPair   the key pair whose public key will be used (non-null)
     * @return {@code true} if the signature is valid
     * @throws NullPointerException if any argument is null
     */
    public static boolean verify(byte[] data, byte[] signature, ECKeyPair keyPair) {
        Objects.requireNonNull(keyPair, "keyPair must not be null");
        return verify(data, signature, keyPair.getPublicKey());
    }
}
