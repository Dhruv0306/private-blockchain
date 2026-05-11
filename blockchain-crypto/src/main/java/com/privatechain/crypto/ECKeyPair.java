package com.privatechain.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Immutable record holding an elliptic-curve key pair generated on the
 * <a href="https://en.bitcoin.it/wiki/Secp256k1">secp256k1</a> curve.
 *
 * <p>Instances are created exclusively by {@link KeyPairGenerator#generateECKeyPair()}
 * or reconstructed from a hex-encoded private key via
 * {@link KeyPairGenerator#fromPrivateKeyHex(String)}.
 * Consumer code should treat this object as an opaque credential holder and never
 * log, serialize, or transmit it in plain form.</p>
 *
 * <h2>Security invariants</h2>
 * <ul>
 *   <li>{@link #toString()} <strong>never</strong> reveals the private key
 *       (NFR-SEC-01). The private-key field is rendered as {@code [REDACTED]}.</li>
 *   <li>{@link #getPrivateKeyHex()} exists for legitimate export flows
 *       (e.g., keystore serialisation) but callers must protect the returned value.</li>
 * </ul>
 *
 * <pre>{@code
 * ECKeyPair keyPair = KeyPairGenerator.generateECKeyPair();
 * String     pubHex  = keyPair.getPublicKeyHex();   // safe to share
 * String     privHex = keyPair.getPrivateKeyHex();  // handle with care
 * }</pre>
 *
 * @see KeyPairGenerator
 * @see ECDSASignatureUtil
 * @since 1.0.0
 */
public final class ECKeyPair {

    static {
        // Ensure BouncyCastle is registered before any key material is accessed.
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    // ─── Fields ───────────────────────────────────────────────────────────────

    /** The JCA public key object. */
    private final PublicKey publicKey;

    /**
     * The JCA private key object.
     * Intentionally package-private to allow {@link ECDSASignatureUtil} direct access
     * without exposing the key to application code via a public getter returning a raw type.
     */
    private final PrivateKey privateKey;

    // ─── Constructor ──────────────────────────────────────────────────────────

    /**
     * Package-private constructor — callers must use {@link KeyPairGenerator}.
     *
     * @param publicKey  JCA public key (non-null)
     * @param privateKey JCA private key (non-null)
     * @throws NullPointerException if either key is null
     */
    ECKeyPair(PublicKey publicKey, PrivateKey privateKey) {
        this.publicKey = Objects.requireNonNull(publicKey, "publicKey must not be null");
        this.privateKey = Objects.requireNonNull(privateKey, "privateKey must not be null");
    }

    // ─── Public key accessors ─────────────────────────────────────────────────

    /**
     * Returns the JCA {@link PublicKey} for this key pair.
     *
     * <p>The public key may be shared freely; it is used to verify ECDSA signatures
     * and to derive blockchain addresses.</p>
     *
     * @return non-null public key
     */
    public PublicKey getPublicKey() {
        return publicKey;
    }

    /**
     * Returns the uncompressed, DER-encoded public key as a lowercase hex string.
     *
     * <p>The hex representation is the canonical form used in peer announcements
     * and address derivation.</p>
     *
     * @return 130-character (65-byte, uncompressed point) hex string
     */
    public String getPublicKeyHex() {
        return HexFormat.of().formatHex(publicKey.getEncoded());
    }

    // ─── Private key accessors ────────────────────────────────────────────────

    /**
     * Returns the JCA {@link PrivateKey} for this key pair.
     *
     * <p><strong>Warning:</strong> handle the returned object with extreme care.
     * Do not log it, pass it to untrusted code, or store it unencrypted.</p>
     *
     * @return non-null private key
     */
    PrivateKey getPrivateKey() {
        return privateKey;
    }

    /**
     * Returns the PKCS#8-encoded private key as a lowercase hex string.
     *
     * <p><strong>Warning:</strong> this value is a secret. The caller is responsible
     * for zeroing the returned {@code String} from memory after use where possible.
     * Only invoke this method in trusted, controlled code paths such as keystore
     * encryption.</p>
     *
     * @return hex-encoded private key bytes
     */
    public String getPrivateKeyHex() {
        return HexFormat.of().formatHex(privateKey.getEncoded());
    }

    // ─── Object overrides ─────────────────────────────────────────────────────

    /**
     * Returns a safe, human-readable summary of this key pair.
     *
     * <p>The private key is <strong>never</strong> included in the output
     * (NFR-SEC-01). The public key is truncated to its first 16 hex characters
     * to keep log lines concise.</p>
     *
     * @return string in the form {@code ECKeyPair{pub=<first16hex>..., priv=[REDACTED]}}
     */
    @Override
    public String toString() {
        String pubHex = getPublicKeyHex();
        // Show only the first 16 characters of the public key — enough to identify
        // the key in logs without bloating the line.
        String pubPreview = pubHex.length() > 16 ? pubHex.substring(0, 16) + "..." : pubHex;
        return "ECKeyPair{pub=" + pubPreview + ", priv=[REDACTED]}";
    }

    /**
     * {@inheritDoc}
     *
     * <p>Equality is determined by the raw encoding of both keys so that
     * two {@code ECKeyPair} instances built from the same hex private key
     * are considered equal.</p>
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ECKeyPair other)) {
            return false;
        }
        return Objects.equals(getPublicKeyHex(), other.getPublicKeyHex())
            && Objects.equals(getPrivateKeyHex(), other.getPrivateKeyHex());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        // Use only the public key hex for hashing to avoid constant-time issues
        // with private key comparisons in hot paths.
        return Objects.hash(getPublicKeyHex());
    }
}
