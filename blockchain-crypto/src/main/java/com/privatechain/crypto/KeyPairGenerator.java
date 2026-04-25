package com.privatechain.crypto;

import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * Factory for generating and reconstructing EC key pairs on the {@code secp256k1} curve.
 *
 * <p>All methods are static. This class cannot be instantiated.
 *
 * <p>Uses BouncyCastle as the JCE provider because the standard JDK does not include
 * the {@code secp256k1} curve in it's named curve registry.
 *
 * <p>The {@link SecureRandom} instance used for key generation is created fresh per
 * call — never reused across calls — to prevent subtle entropy-reuse attacks.
 *
 * @see ECKeyPair
 * @see ECDSASignatureUtil
 */
public final class KeyPairGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(KeyPairGenerator.class);
    private static final String CURVE_NAME = "secp256k1";
    private static final String KEY_ALGORITHM = "EC";
    private static final String PROVIDER = BouncyCastleProvider.PROVIDER_NAME;

    static {
        if (Security.getProvider(PROVIDER) == null) {
            Security.addProvider(new BouncyCastleProvider());
            LOG.debug("BouncyCastle provider registered by KeyPairGenerator");
        }
    }

    /** Utility class — no instances. */
    private KeyPairGenerator() {
        throw new UnsupportedOperationException("KeyPairGenerator is a utility class");
    }

    /**
     * Generates a fresh EC key pair on the {@code secp256k1} curve.
     *
     * <p>Each call produces a cryptographically independent key pair using a new
     * {@link SecureRandom} instance seeded by the OS entropy source.
     *
     * @return a new {@link ECKeyPair} with a randomly generated public and private key
     * @throws CryptoException if the underlying JCE provider fails (should not occur
     *                         in a correctly configured environment with BouncyCastle)
     */
    public static ECKeyPair generateECKeyPair() {
        try {
            final ECNamedCurveParameterSpec curveSpec =
                ECNamedCurveTable.getParameterSpec(CURVE_NAME);

            final java.security.KeyPairGenerator generator =
                java.security.KeyPairGenerator.getInstance(KEY_ALGORITHM, PROVIDER);

            generator.initialize(curveSpec, new SecureRandom());

            final KeyPair keyPair = generator.generateKeyPair();
            LOG.debug("Generated new secp256k1 key pair");
            return new ECKeyPair(keyPair.getPublic(), keyPair.getPrivate());

        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new CryptoException(
                "EC key algorithm or BouncyCastle provider not available. "
                    + "Ensure bcprov-jdk18on is on the classpath.", e
            );
        } catch (InvalidAlgorithmParameterException e) {
            throw new CryptoException(
                "Invalid curve specification for: " + CURVE_NAME, e
            );
        }
    }

    /**
     * Reconstructs an {@link ECKeyPair} from hex-encoded DER representations.
     *
     * <p>The expected encoding formats are:
     * <ul>
     *   <li>Public key: X.509 SubjectPublicKeyInfo DER (produced by
     *       {@link ECKeyPair#toPublicKeyHex()})</li>
     *   <li>Private key: PKCS#8 DER (produced by
     *       {@link ECKeyPair#toPrivateKeyHex()})</li>
     * </ul>
     *
     * @param publicKeyHex  lowercase hex-encoded X.509 DER public key; must not be {@code null}
     * @param privateKeyHex lowercase hex-encoded PKCS#8 DER private key; must not be {@code null}
     * @return reconstructed {@link ECKeyPair}
     * @throws IllegalArgumentException if either argument is {@code null} or malformed
     * @throws CryptoException          if key reconstruction fails
     */
    public static ECKeyPair fromHex(final String publicKeyHex, final String privateKeyHex) {
        if (publicKeyHex == null) {
            throw new IllegalArgumentException("publicKeyHex must not be null");
        }
        if (privateKeyHex == null) {
            throw new IllegalArgumentException("privateKeyHex must not be null");
        }
        try {
            final KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM, PROVIDER);

            final PublicKey publicKey = keyFactory.generatePublic(
                new X509EncodedKeySpec(HashUtil.hexToBytes(publicKeyHex))
            );
            final PrivateKey privateKey = keyFactory.generatePrivate(
                new PKCS8EncodedKeySpec(HashUtil.hexToBytes(privateKeyHex))
            );
            return new ECKeyPair(publicKey, privateKey);

        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new CryptoException("EC key factory not available", e);
        } catch (InvalidKeySpecException e) {
            throw new CryptoException(
                "Invalid key encoding. Ensure hex strings were produced by ECKeyPair.toPublicKeyHex()"
                    + " / toPrivateKeyHex()", e
            );
        }
    }

    /**
     * Reconstructs a {@link PublicKey} from its hex-encoded X.509 DER representation.
     *
     * <p>Used when only the public key is needed (e.g., signature verification).
     *
     * @param publicKeyHex lowercase hex-encoded X.509 DER public key; must not be {@code null}
     * @return reconstructed {@link PublicKey}
     * @throws IllegalArgumentException if {@code publicKeyHex} is {@code null}
     * @throws CryptoException          if reconstruction fails
     */
    public static PublicKey publicKeyFromHex(final String publicKeyHex) {
        if (publicKeyHex == null) {
            throw new IllegalArgumentException("publicKeyHex must not be null");
        }
        try {
            final KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM, PROVIDER);
            return keyFactory.generatePublic(
                new X509EncodedKeySpec(HashUtil.hexToBytes(publicKeyHex))
            );
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new CryptoException("EC key factory not available", e);
        } catch (InvalidKeySpecException | IllegalArgumentException e) {
            throw new CryptoException("Invalid public key encoding: " + publicKeyHex, e);
        }
    }
}
