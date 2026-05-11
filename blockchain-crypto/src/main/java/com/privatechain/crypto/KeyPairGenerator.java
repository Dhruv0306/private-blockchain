package com.privatechain.crypto;

import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECPrivateKeySpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Factory for generating and reconstructing elliptic-curve key pairs on the
 * <a href="https://en.bitcoin.it/wiki/Secp256k1">secp256k1</a> curve.
 *
 * <p>All random number generation uses {@link SecureRandom} sourced from the
 * operating-system entropy pool, as mandated by NFR-SEC-05. The class is a
 * utility and cannot be instantiated.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Generate a fresh key pair
 * ECKeyPair fresh = KeyPairGenerator.generateECKeyPair();
 *
 * // Reconstruct from a stored private key
 * ECKeyPair restored = KeyPairGenerator.fromPrivateKeyHex(storedHex);
 * }</pre>
 *
 * @see ECKeyPair
 * @see ECDSASignatureUtil
 * @since 1.0.0
 */
public final class KeyPairGenerator {

    /** secp256k1 curve name used in BouncyCastle's named-curve table. */
    private static final String CURVE_NAME = "secp256k1";

    /** JCA algorithm identifier for elliptic-curve keys. */
    private static final String EC_ALGORITHM = "EC";

    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * Utility class — no instances.
     */
    private KeyPairGenerator() {
        throw new UnsupportedOperationException("KeyPairGenerator is a utility class");
    }

    // ─── Key generation ───────────────────────────────────────────────────────

    /**
     * Generates a fresh secp256k1 EC key pair using a {@link SecureRandom} entropy source.
     *
     * <p>The returned {@link ECKeyPair} contains both the private and public key.
     * Callers must protect the private key and never expose it in logs or serialized form
     * without encryption (NFR-SEC-01).</p>
     *
     * @return a new, cryptographically random {@link ECKeyPair}
     * @throws IllegalStateException if the JCA provider is missing (should not occur in practice)
     */
    public static ECKeyPair generateECKeyPair() {
        try {
            // Use BouncyCastle's KeyPairGenerator directly for secp256k1
            java.security.KeyPairGenerator kpg =
                java.security.KeyPairGenerator.getInstance(EC_ALGORITHM, "BC");

            ECNamedCurveParameterSpec spec = ECNamedCurveTable.getParameterSpec(CURVE_NAME);
            kpg.initialize(spec, new SecureRandom());

            KeyPair kp = kpg.generateKeyPair();
            return new ECKeyPair(kp.getPublic(), kp.getPrivate());

        } catch (NoSuchAlgorithmException | NoSuchProviderException
                 | InvalidAlgorithmParameterException e) {
            throw new IllegalStateException(
                "Failed to generate secp256k1 key pair: " + e.getMessage(), e);
        }
    }

    // ─── Reconstruction from hex ──────────────────────────────────────────────

    /**
     * Reconstructs an {@link ECKeyPair} from a hex-encoded PKCS#8 private key.
     *
     * <p>The public key is re-derived from the private scalar via the secp256k1
     * generator point, so only the private key bytes are required as input.</p>
     *
     * <p><strong>Security note:</strong> callers must ensure the {@code privateKeyHex}
     * string is handled securely (e.g., read from an encrypted keystore, not hard-coded).</p>
     *
     * @param privateKeyHex lowercase hex-encoded PKCS#8 private key (non-null, non-blank)
     * @return the reconstructed {@link ECKeyPair}
     * @throws NullPointerException     if privateKeyHex is null
     * @throws IllegalArgumentException if privateKeyHex is blank or not valid hex
     * @throws IllegalStateException    if the BouncyCastle provider fails
     */
    public static ECKeyPair fromPrivateKeyHex(String privateKeyHex) {
        Objects.requireNonNull(privateKeyHex, "privateKeyHex must not be null");
        if (privateKeyHex.isBlank()) {
            throw new IllegalArgumentException("privateKeyHex must not be blank");
        }

        try {
            byte[] pkcs8Bytes = HexFormat.of().parseHex(privateKeyHex);
            KeyFactory keyFactory = KeyFactory.getInstance(EC_ALGORITHM, "BC");

            // Re-create the private key from PKCS#8 bytes
            PrivateKey privateKey = keyFactory.generatePrivate(
                new java.security.spec.PKCS8EncodedKeySpec(pkcs8Bytes));

            // Re-derive the public key from the private scalar
            PublicKey publicKey = derivePublicKey(privateKey, keyFactory);

            return new ECKeyPair(publicKey, privateKey);

        } catch (IllegalArgumentException e) {
            // HexFormat.parseHex throws IllegalArgumentException on malformed hex
            throw new IllegalArgumentException(
                "privateKeyHex is not valid hexadecimal: " + e.getMessage(), e);
        } catch (java.security.spec.InvalidKeySpecException
                 | NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new IllegalStateException(
                "Failed to reconstruct key pair from hex: " + e.getMessage(), e);
        }
    }

    /**
     * Reconstructs an {@link ECKeyPair} from a raw 32-byte private scalar (big-integer, big-endian).
     *
     * <p>This overload is useful when working with wallets that store the raw private scalar
     * rather than the full PKCS#8 structure (e.g., Bitcoin-style WIF / raw hex).</p>
     *
     * @param rawPrivateScalarHex 64-character (32-byte) lowercase hex private scalar (non-null, non-blank)
     * @return the reconstructed {@link ECKeyPair}
     * @throws NullPointerException     if rawPrivateScalarHex is null
     * @throws IllegalArgumentException if rawPrivateScalarHex is blank, not valid hex,
     *                                  or not a valid secp256k1 scalar
     * @throws IllegalStateException    if the BouncyCastle provider fails
     */
    public static ECKeyPair fromRawPrivateScalar(String rawPrivateScalarHex) {
        Objects.requireNonNull(rawPrivateScalarHex, "rawPrivateScalarHex must not be null");
        if (rawPrivateScalarHex.isBlank()) {
            throw new IllegalArgumentException("rawPrivateScalarHex must not be blank");
        }

        try {
            byte[] rawBytes = HexFormat.of().parseHex(rawPrivateScalarHex);
            BigInteger privateScalar = new BigInteger(1, rawBytes);

            ECNamedCurveParameterSpec spec = ECNamedCurveTable.getParameterSpec(CURVE_NAME);
            KeyFactory keyFactory = KeyFactory.getInstance(EC_ALGORITHM, "BC");

            // Derive private key from raw scalar
            ECPrivateKeySpec privateKeySpec = new ECPrivateKeySpec(privateScalar, spec);
            PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);

            // Derive public key: Q = d * G
            ECPoint publicPoint = spec.getG().multiply(privateScalar).normalize();
            ECPublicKeySpec publicKeySpec = new ECPublicKeySpec(publicPoint, spec);
            PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);

            return new ECKeyPair(publicKey, privateKey);

        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "rawPrivateScalarHex is not valid hexadecimal or scalar: " + e.getMessage(), e);
        } catch (java.security.spec.InvalidKeySpecException
                 | NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new IllegalStateException(
                "Failed to reconstruct key pair from raw scalar: " + e.getMessage(), e);
        }
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    /**
     * Re-derives the EC public key from an existing private key by multiplying the
     * curve's generator point by the private scalar {@code d}: Q = d × G.
     *
     * @param privateKey the existing private key
     * @param keyFactory pre-created {@link KeyFactory} with the BC provider
     * @return the corresponding public key
     * @throws java.security.spec.InvalidKeySpecException if the key spec is malformed
     */
    private static PublicKey derivePublicKey(PrivateKey privateKey, KeyFactory keyFactory)
        throws java.security.spec.InvalidKeySpecException {

        // Extract the private scalar from the PKCS#8 key
        org.bouncycastle.jce.interfaces.ECPrivateKey bcPrivKey =
            (org.bouncycastle.jce.interfaces.ECPrivateKey) privateKey;

        ECNamedCurveParameterSpec spec = ECNamedCurveTable.getParameterSpec(CURVE_NAME);

        // Q = d * G (scalar multiplication on the secp256k1 curve)
        ECPoint publicPoint = spec.getG().multiply(bcPrivKey.getD()).normalize();
        ECPublicKeySpec publicKeySpec = new ECPublicKeySpec(publicPoint, spec);
        return keyFactory.generatePublic(publicKeySpec);
    }
}
