package com.privatechain.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.SignatureException;

/**
 * ECDSA signing and verification utilities for the private-blockchain library.
 *
 * <p>All methods are static. This class cannot be instantiated.
 *
 * <p>Algorithm: {@code SHA256withECDSA} via BouncyCastle. Keys must be on
 * the {@code secp256k1} curve — use {@link KeyPairGenerator} to produce
 * compatible keys.
 *
 * <p>The signature output is DER-encoded and wrapped in a
 * {@link BlockchainSignature} value object whose {@link BlockchainSignature#toString()}
 * is always a safe placeholder — raw bytes are never exposed through logging.
 *
 * <p>Usage example:
 * <pre>{@code
 *   ECKeyPair keyPair = KeyPairGenerator.generateECKeyPair();
 *   byte[] data = "tx:abc123".getBytes(StandardCharsets.UTF_8);
 *
 *   BlockchainSignature sig = ECDSASignatureUtil.sign(data, keyPair.privateKey());
 *   boolean valid = ECDSASignatureUtil.verify(data, sig, keyPair.publicKey()); // true
 * }</pre>
 *
 * @see KeyPairGenerator
 * @see BlockchainSignature
 */
public final class ECDSASignatureUtil {

    private static final Logger LOG = LoggerFactory.getLogger(ECDSASignatureUtil.class);
    private static final String ALGORITHM = "SHA256withECDSA";
    private static final String PROVIDER  = BouncyCastleProvider.PROVIDER_NAME;

    static {
        if (Security.getProvider(PROVIDER) == null) {
            Security.addProvider(new BouncyCastleProvider());
            LOG.debug("BouncyCastle provider registered by ECDSASignatureUtil");
        }
    }

    /** Utility class — no instances. */
    private ECDSASignatureUtil() {
        throw new UnsupportedOperationException("ECDSASignatureUtil is a utility class");
    }

    /**
     * Signs the given raw bytes with the supplied EC private key.
     *
     * <p>Data is signed as-is. If signing a string, encode it to bytes
     * first using {@code String.getBytes(StandardCharsets.UTF_8)}.
     *
     * @param data       the raw bytes to sign; must not be {@code null}
     * @param privateKey the signer's EC private key (secp256k1); must not be {@code null}
     * @return a {@link BlockchainSignature} wrapping the DER-encoded signature
     * @throws IllegalArgumentException if {@code data} or {@code privateKey} is {@code null}
     * @throws CryptoException          if the signing operation fails
     */
    public static BlockchainSignature sign(final byte[] data, final PrivateKey privateKey) {
        requireNonNull(data,       "data");
        requireNonNull(privateKey, "privateKey");
        try {
            final java.security.Signature signer =
                java.security.Signature.getInstance(ALGORITHM, PROVIDER);
            signer.initSign(privateKey);
            signer.update(data);
            final byte[] derBytes = signer.sign();
            LOG.debug("Signed {} bytes → {} byte DER signature", data.length, derBytes.length);
            return new BlockchainSignature(derBytes);
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new CryptoException(
                "SHA256withECDSA not available. Ensure bcprov-jdk18on is on the classpath.", e);
        } catch (InvalidKeyException e) {
            throw new CryptoException(
                "Invalid private key. Key must be a secp256k1 EC private key.", e);
        } catch (SignatureException e) {
            throw new CryptoException("Signing operation failed unexpectedly.", e);
        }
    }

    /**
     * Verifies a {@link BlockchainSignature} against the original data and public key.
     *
     * <p>Returns {@code false} — not an exception — when the signature is simply wrong
     * (wrong key, tampered data). A {@link CryptoException} is thrown only when the
     * verification mechanism itself fails (missing provider, invalid key format).
     *
     * @param data      the original raw bytes that were signed; must not be {@code null}
     * @param signature the signature to verify; must not be {@code null}
     * @param publicKey the signer's EC public key (secp256k1); must not be {@code null}
     * @return {@code true} if the signature is valid; {@code false} if invalid or tampered
     * @throws IllegalArgumentException if any argument is {@code null}
     * @throws CryptoException          if the verification mechanism fails
     */
    public static boolean verify(
        final byte[] data,
        final BlockchainSignature signature,
        final PublicKey publicKey) {
        requireNonNull(data,      "data");
        requireNonNull(signature, "signature");
        requireNonNull(publicKey, "publicKey");
        try {
            final java.security.Signature verifier =
                java.security.Signature.getInstance(ALGORITHM, PROVIDER);
            verifier.initVerify(publicKey);
            verifier.update(data);
            return verifier.verify(signature.derBytes());
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new CryptoException("SHA256withECDSA not available.", e);
        } catch (InvalidKeyException e) {
            throw new CryptoException(
                "Invalid public key. Key must be a secp256k1 EC public key.", e);
        } catch (SignatureException e) {
            // Malformed DER bytes — treat as invalid signature, not an error
            LOG.warn("Malformed signature bytes during verification: {}", e.getMessage());
            return false;
        }
    }

    // ── internal ─────────────────────────────────────────────────────────────

    private static void requireNonNull(final Object value, final String name) {
        if (value == null) {
            throw new IllegalArgumentException("'" + name + "' must not be null");
        }
    }
}
