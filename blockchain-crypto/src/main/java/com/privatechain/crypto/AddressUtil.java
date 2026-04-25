package com.privatechain.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.PublicKey;
import java.security.Security;

/**
 * Derives node and wallet addresses from EC public keys.
 *
 * <p>All methods are static. This class cannot be instantiated.
 *
 * <p>Address derivation algorithm (Bitcoin-style):
 * <ol>
 *   <li>Encode the public key as X.509 DER bytes.</li>
 *   <li>Compute {@code SHA-256(publicKeyBytes)}.</li>
 *   <li>Compute {@code RIPEMD-160(step2)}.</li>
 *   <li>Hex-encode the 20-byte result → 40-character address string.</li>
 * </ol>
 *
 * <p>The result is a deterministic, 40-character lowercase hex string that
 * uniquely identifies a node or wallet within the blockchain network.
 *
 * <p>Unlike Bitcoin, this library does not apply Base58Check encoding or a
 * version prefix — the raw hex address keeps the implementation simple and
 * avoids the need for a checksum library in {@code blockchain-core}.
 *
 * @see KeyPairGenerator
 * @see HashUtil
 */
public final class AddressUtil {

    /** Length of a valid address hex string (20 bytes × 2 hex chars per byte). */
    public static final int ADDRESS_HEX_LENGTH = 40;

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /** Utility class — no instances. */
    private AddressUtil() {
        throw new UnsupportedOperationException("AddressUtil is a utility class");
    }

    /**
     * Derives a blockchain address from a {@link PublicKey}.
     *
     * <p>Algorithm: {@code hex( RIPEMD160( SHA256( publicKey.getEncoded() ) ) )}
     *
     * @param publicKey the EC public key; must not be {@code null}
     * @return a 40-character lowercase hex address string
     * @throws IllegalArgumentException if {@code publicKey} is {@code null}
     */
    public static String deriveAddress(final PublicKey publicKey) {
        if (publicKey == null) {
            throw new IllegalArgumentException("publicKey must not be null");
        }
        final byte[] pubKeyBytes  = publicKey.getEncoded();
        final byte[] sha256Bytes  = HashUtil.hexToBytes(HashUtil.sha256(pubKeyBytes));
        final byte[] ripemdBytes  = HashUtil.ripemd160(sha256Bytes);
        return HashUtil.bytesToHex(ripemdBytes);
    }

    /**
     * Derives a blockchain address from a hex-encoded public key string.
     *
     * <p>Convenience overload for cases where the public key is already in
     * its hex representation (e.g. stored in a database or received over the wire).
     *
     * @param publicKeyHex lowercase hex-encoded X.509 DER public key;
     *                     must not be {@code null}
     * @return a 40-character lowercase hex address string
     * @throws IllegalArgumentException if {@code publicKeyHex} is {@code null}
     * @throws CryptoException          if the hex cannot be decoded into a valid key
     */
    public static String deriveAddress(final String publicKeyHex) {
        if (publicKeyHex == null) {
            throw new IllegalArgumentException("publicKeyHex must not be null");
        }
        final PublicKey publicKey = KeyPairGenerator.publicKeyFromHex(publicKeyHex);
        return deriveAddress(publicKey);
    }

    /**
     * Returns {@code true} if the given string is a syntactically valid address.
     *
     * <p>A valid address is exactly {@value #ADDRESS_HEX_LENGTH} lowercase hex characters.
     * This method does <em>not</em> verify that the address belongs to any known key —
     * it only checks the format.
     *
     * @param address the address string to check; may be {@code null}
     * @return {@code true} if the address has the correct format
     */
    public static boolean isValidAddress(final String address) {
        if (address == null || address.length() != ADDRESS_HEX_LENGTH) {
            return false;
        }
        return address.matches("[0-9a-f]{40}");
    }
}
