package com.privatechain.core.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Lightweight, immutable record representing the header of a {@link Block}.
 *
 * <p>The header contains all metadata needed for chain validation without loading
 * the full transaction list, satisfying FR-CORE-05. It is a Java {@code record}
 * to guarantee immutability and provide auto-generated equals/hashCode/toString.</p>
 *
 * <p>Fields follow Bitcoin-style naming conventions where applicable:</p>
 * <ul>
 *   <li>{@code version} — protocol version for future-compatibility</li>
 *   <li>{@code bits} — compact difficulty target (PoW specific; ignored by other engines)</li>
 *   <li>{@code nonce} — mutable-during-mining counter; fixed after block is sealed</li>
 *   <li>{@code merkleRoot} — SHA-256 root of the transaction Merkle tree</li>
 *   <li>{@code timestamp} — wall-clock time at block creation (UTC)</li>
 * </ul>
 *
 * @param version    protocol version (must be &ge; 1)
 * @param bits       compact difficulty target used by PoW consensus; 0 for non-PoW engines
 * @param nonce      proof-of-work nonce discovered during mining
 * @param merkleRoot hex-encoded SHA-256 Merkle root of the block's transactions
 * @param timestamp  instant at which the block was produced (UTC, non-null)
 * @see Block
 * @since 1.0.0
 */
public record BlockHeader(
    int version,
    int bits,
    long nonce,
    String merkleRoot,
    Instant timestamp) {

    // ─── Compact representation of an empty Merkle tree ───────────────────────
    /**
     * Sentinel Merkle root used when a block contains no transactions.
     */
    public static final String EMPTY_MERKLE_ROOT =
        "0000000000000000000000000000000000000000000000000000000000000000";

    /**
     * Canonical constructor with validation.
     *
     * @param version    protocol version (must be &ge; 1)
     * @param bits       compact difficulty target (&ge; 0)
     * @param nonce      mining nonce
     * @param merkleRoot Merkle root hex string (non-null, non-blank)
     * @param timestamp  block timestamp (non-null)
     * @throws IllegalArgumentException if version &lt; 1, bits &lt; 0, or strings are blank
     * @throws NullPointerException     if merkleRoot or timestamp is null
     */
    public BlockHeader {
        if (version < 1) {
            throw new IllegalArgumentException("Block version must be >= 1, got: " + version);
        }
        if (bits < 0) {
            throw new IllegalArgumentException("Bits must be >= 0, got: " + bits);
        }
        Objects.requireNonNull(merkleRoot, "merkleRoot must not be null");
        if (merkleRoot.isBlank()) {
            throw new IllegalArgumentException("merkleRoot must not be blank");
        }
        Objects.requireNonNull(timestamp, "timestamp must not be null");
    }

    // ─── Factory methods ───────────────────────────────────────────────────────

    /**
     * Creates a {@code BlockHeader} with the current UTC timestamp and version 1.
     *
     * <p>Convenience factory for common use cases where the caller does not need
     * to control the timestamp (e.g., tests, quick demos).</p>
     *
     * @param bits       compact difficulty target
     * @param nonce      mining nonce
     * @param merkleRoot Merkle root hex string
     * @return a new {@code BlockHeader} timestamped {@code Instant.now()}
     */
    public static BlockHeader of(int bits, long nonce, String merkleRoot) {
        return new BlockHeader(1, bits, nonce, merkleRoot, Instant.now());
    }

    /**
     * Creates a genesis-block header with a zero nonce and the empty Merkle root.
     *
     * @return a header suitable for use as the genesis block header
     */
    public static BlockHeader genesis() {
        return new BlockHeader(1, 0x1d00ffff, 0L, EMPTY_MERKLE_ROOT, Instant.EPOCH);
    }

    // ─── Builder ──────────────────────────────────────────────────────────────

    /**
     * Returns a new {@link Builder} pre-populated with sensible defaults.
     *
     * @return a mutable builder for constructing a {@code BlockHeader}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link BlockHeader}.
     *
     * <p>All fields have defaults (version=1, bits=0, nonce=0, merkleRoot=EMPTY, now)
     * so callers only need to set the values they care about.</p>
     */
    public static final class Builder {

        private int version = 1;
        private int bits = 0;
        private long nonce = 0L;
        private String merkleRoot = EMPTY_MERKLE_ROOT;
        private Instant timestamp = Instant.now();

        /**
         * Private constructor — use {@link BlockHeader#builder()}.
         */
        private Builder() {
        }

        /**
         * Sets the protocol version.
         *
         * @param version protocol version (&ge; 1)
         * @return this builder
         */
        public Builder version(int version) {
            this.version = version;
            return this;
        }

        /**
         * Sets the compact difficulty target (PoW).
         *
         * @param bits compact target bits
         * @return this builder
         */
        public Builder bits(int bits) {
            this.bits = bits;
            return this;
        }

        /**
         * Sets the proof-of-work nonce.
         *
         * @param nonce mining nonce
         * @return this builder
         */
        public Builder nonce(long nonce) {
            this.nonce = nonce;
            return this;
        }

        /**
         * Sets the Merkle root hex string.
         *
         * @param merkleRoot 64-character hex string
         * @return this builder
         */
        public Builder merkleRoot(String merkleRoot) {
            this.merkleRoot = merkleRoot;
            return this;
        }

        /**
         * Sets the block timestamp.
         *
         * @param timestamp UTC instant of block creation (non-null)
         * @return this builder
         */
        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        /**
         * Builds and returns the {@link BlockHeader}.
         *
         * @return a new immutable {@code BlockHeader}
         * @throws IllegalArgumentException if any field fails validation
         */
        public BlockHeader build() {
            return new BlockHeader(version, bits, nonce, merkleRoot, timestamp);
        }
    }
}
