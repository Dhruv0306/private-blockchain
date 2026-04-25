package com.privatechain.crypto;

import java.util.Arrays;

/**
 * An immutable value object wrapping a DER-encoded ECDSA signature.
 *
 * <p>Instances are produced by {@link ECDSASignatureUtil#sign} and consumed by
 * {@link ECDSASignatureUtil#verify}. They are also stored on {@code Transaction}
 * objects after signing.
 *
 * <p><strong>Security contract:</strong> {@link #toString()} never exposes the
 * raw signature bytes — it returns only the placeholder string
 * {@code "[ECDSA signature]"}. This prevents accidental logging of signature
 * material. Use {@link #toHex()} when a serializable representation is needed.
 *
 * @see ECDSASignatureUtil
 */
public final class BlockchainSignature {

    private final byte[] derBytes;

    /**
     * Constructs a {@code BlockchainSignature} from raw DER-encoded bytes.
     *
     * <p>The byte array is defensively copied — mutations to the supplied
     * array after construction do not affect this instance.
     *
     * @param derBytes DER-encoded ECDSA signature bytes; must not be {@code null} or empty
     * @throws IllegalArgumentException if {@code derBytes} is {@code null} or empty
     */
    public BlockchainSignature(final byte[] derBytes) {
        if (derBytes == null) {
            throw new IllegalArgumentException("derBytes must not be null");
        }
        if (derBytes.length == 0) {
            throw new IllegalArgumentException("derBytes must not be empty");
        }
        this.derBytes = Arrays.copyOf(derBytes, derBytes.length);
    }

    /**
     * Returns a defensive copy of the raw DER-encoded signature bytes.
     *
     * <p>Mutations to the returned array do not affect this instance.
     *
     * @return copy of the DER-encoded signature bytes
     */
    public byte[] derBytes() {
        return Arrays.copyOf(derBytes, derBytes.length);
    }

    /**
     * Returns the signature encoded as a lowercase hex string.
     *
     * <p>This is the preferred form for JSON serialization and storage.
     *
     * @return lowercase hex string representing the DER bytes
     */
    public String toHex() {
        return HashUtil.bytesToHex(derBytes);
    }

    /**
     * Reconstructs a {@code BlockchainSignature} from a hex-encoded string.
     *
     * <p>This is the inverse of {@link #toHex()}.
     *
     * @param hex lowercase hex-encoded DER signature; must not be {@code null} or empty
     * @return the reconstructed {@code BlockchainSignature}
     * @throws IllegalArgumentException if {@code hex} is {@code null}, empty, or has odd length
     */
    public static BlockchainSignature fromHex(final String hex) {
        if (hex == null || hex.isEmpty()) {
            throw new IllegalArgumentException("hex must not be null or empty");
        }
        return new BlockchainSignature(HashUtil.hexToBytes(hex));
    }

    /**
     * Returns a safe placeholder string — never exposes the raw bytes.
     *
     * @return the fixed string {@code "[ECDSA signature]"}
     */
    @Override
    public String toString() {
        return "[ECDSA signature]";
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof BlockchainSignature)) {
            return false;
        }
        final BlockchainSignature other = (BlockchainSignature) obj;
        return Arrays.equals(derBytes, other.derBytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(derBytes);
    }
}
