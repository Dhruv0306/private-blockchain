package com.privatechain.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jetbrains.annotations.NotNull;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;

/**
 * An immutable EC (Elliptic Curve) key pair using the {@code secp256k1} curve.
 *
 * <p>This record holds both the {@link PublicKey} and {@link PrivateKey}.
 * It is produced exclusively by {@link KeyPairGenerator} and consumed by
 * {@link ECDSASignatureUtil} (signing) and {@link AddressUtil} (address derivation).
 *
 * <p><strong>Security contract:</strong>
 * <ul>
 *   <li>{@link #toString()} never exposes the private key — it returns a
 *       safe summary containing only the public key hex prefix.</li>
 *   <li>The private key is accessible only via {@link #privateKey()} to
 *       callers with direct access to this record. Never log or serialize
 *       the private key in plaintext.</li>
 * </ul>
 *
 * @param publicKey  the EC public key; never {@code null}
 * @param privateKey the EC private key; never {@code null}
 * @see KeyPairGenerator
 * @see ECDSASignatureUtil
 * @see AddressUtil
 */
public record ECKeyPair(PublicKey publicKey, PrivateKey privateKey) {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * Compact constructor — validates that neither key is {@code null}.
     *
     * @param publicKey  the public key; must not be {@code null}
     * @param privateKey the private key; must not be {@code null}
     * @throws IllegalArgumentException if either key is {@code null}
     */
    public ECKeyPair {
        if (publicKey == null) {
            throw new IllegalArgumentException("publicKey must not be null");
        }
        if (privateKey == null) {
            throw new IllegalArgumentException("privateKey must not be null");
        }
    }

    /**
     * Returns the public key encoded as a lowercase hex string.
     *
     * <p>Uses the X.509 / SubjectPublicKeyInfo DER encoding of the public key.
     * The result is stable across JVM restarts for the same key.
     *
     * @return lowercase hex-encoded public key bytes
     */
    public String toPublicKeyHex() {
        return HashUtil.bytesToHex(publicKey.getEncoded());
    }

    /**
     * Returns the private key encoded as a lowercase hex string.
     *
     * <p>Uses the PKCS#8 DER encoding of the private key.
     *
     * <p><strong>Warning:</strong> the caller is responsible for protecting
     * this value. Never log, transmit unencrypted, or persist without encryption.
     *
     * @return lowercase hex-encoded private key bytes
     */
    public String toPrivateKeyHex() {
        return HashUtil.bytesToHex(privateKey.getEncoded());
    }

    /**
     * Returns a safe string representation.
     *
     * <p>The private key is <strong>never</strong> included.
     * Only the first 16 characters of the public key hex are shown
     * to assist debugging without leaking the full key.
     *
     * @return safe debug string, e.g. {@code ECKeyPair[pubKey=3056301006...]}
     */
    @Override
    @NotNull
    public String toString() {
        final String pubHex = toPublicKeyHex();
        final String preview = pubHex.length() > 16 ? pubHex.substring(0, 16) + "..." : pubHex;
        return "ECKeyPair[pubKey=" + preview + "]";
    }
}
