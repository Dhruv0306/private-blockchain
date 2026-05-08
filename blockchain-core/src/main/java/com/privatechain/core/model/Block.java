package com.privatechain.core.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Immutable unit of the blockchain containing a list of transactions and
 * a cryptographic link to the previous block (FR-CORE-01).
 *
 * <p>Every {@code Block} is self-contained: its {@link #hash} is computed from
 * the block's own fields (including {@code previousHash} and the Merkle root
 * of its transactions) during construction and stored immutably. Tampering with
 * any field of a block — or any transaction inside it — invalidates the hash, which
 * in turn invalidates every subsequent block's {@code previousHash} link, making
 * fraud detectable by {@link com.privatechain.core.builder.Blockchain#isChainValid()}.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * Block block = Block.builder()
 *     .index(1)
 *     .previousHash(genesisBlock.getHash())
 *     .transactions(txList)
 *     .header(BlockHeader.builder().nonce(42L).build())
 *     .build();
 * }</pre>
 *
 * @see BlockHeader
 * @see Transaction
 * @since 1.0.0
 */
public final class Block {

    // ─── Genesis block sentinel ────────────────────────────────────────────
    /**
     * The {@code previousHash} value of the genesis block (index 0).
     * Defined as 64 hex-encoded zero bytes per FR-CORE-07.
     */
    public static final String GENESIS_PREVIOUS_HASH =
        "0000000000000000000000000000000000000000000000000000000000000000";

    // ─── Fields ───────────────────────────────────────────────────────────────

    /**
     * Sequential position of this block in the chain (0 = genesis).
     */
    private final int index;

    /**
     * Lightweight header containing nonce, Merkle root, timestamp, etc.
     */
    private final BlockHeader header;

    /**
     * SHA-256 hash of the previous block in the chain.
     */
    private final String previousHash;

    /**
     * SHA-256 hash of this block, computed deterministically from all other fields.
     * Stored so repeated calls to {@link #getHash()} are O(1).
     */
    private final String hash;

    /**
     * Ordered, immutable list of transactions included in this block.
     */
    private final List<Transaction> transactions;

    // ─── Constructor (used by builder and Jackson) ─────────────────────────

    /**
     * Constructs a fully formed, immutable block.
     *
     * <p>The {@code hash} parameter is stored as-is (not recomputed). Callers
     * constructing blocks from trusted storage should pass the pre-computed hash;
     * new blocks should be created via {@link Builder#build()} which computes the
     * hash automatically.</p>
     *
     * @param index        block index (&ge; 0)
     * @param header       block header (non-null)
     * @param previousHash SHA-256 hex of the preceding block (non-null)
     * @param hash         SHA-256 hex of this block (non-null)
     * @param transactions transactions in this block (non-null; may be empty)
     * @throws NullPointerException     if any argument is null
     * @throws IllegalArgumentException if index is negative
     */
    @JsonCreator
    public Block(
        @JsonProperty("index") int index,
        @JsonProperty("header") BlockHeader header,
        @JsonProperty("previousHash") String previousHash,
        @JsonProperty("hash") String hash,
        @JsonProperty("transactions") List<Transaction> transactions) {

        if (index < 0) {
            throw new IllegalArgumentException("Block index must be >= 0, got: " + index);
        }
        this.index = index;
        this.header = Objects.requireNonNull(header, "header must not be null");
        this.previousHash = Objects.requireNonNull(previousHash, "previousHash must not be null");
        this.hash = Objects.requireNonNull(hash, "hash must not be null");
        this.transactions = Collections.unmodifiableList(
            new ArrayList<>(Objects.requireNonNull(transactions, "transactions must not be null")));
    }

    // ─── Hash computation ─────────────────────────────────────────────────────

    /**
     * Computes the SHA-256 hash of a block from its constituent fields.
     *
     * <p>The hash input is a deterministic canonical string formed by concatenating:
     * {@code index}, {@code previousHash}, {@code merkleRoot}, {@code timestamp},
     * {@code version}, {@code bits}, and {@code nonce}. This mirrors Bitcoin's
     * double-hash approach but uses a single SHA-256 for simplicity; callers that
     * require double-SHA-256 should use {@code blockchain-crypto}'s {@code HashUtil}.</p>
     *
     * @param index        block index
     * @param previousHash hex hash of the previous block
     * @param header       block header (supplies nonce, Merkle root, etc.)
     * @return hex-encoded SHA-256 digest of the canonical input
     * @throws IllegalStateException if SHA-256 is not available (should never occur on JDK 17+)
     */
    public static String computeHash(int index, String previousHash, BlockHeader header) {
        String input = index
            + previousHash
            + header.merkleRoot()
            + header.timestamp().toString()
            + header.version()
            + header.bits()
            + header.nonce();

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed by the JDK spec — this branch is unreachable in practice
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    // ─── Accessors ────────────────────────────────────────────────────────────

    /**
     * Returns a new, empty {@link Builder}.
     *
     * @return a mutable builder for constructing a {@code Block}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the zero-based index of this block in the chain.
     *
     * @return block index (&ge; 0)
     */
    public int getIndex() {
        return index;
    }

    /**
     * Returns the block header containing version, nonce, Merkle root, and timestamp.
     *
     * @return non-null {@link BlockHeader}
     */
    public BlockHeader getHeader() {
        return header;
    }

    /**
     * Returns the SHA-256 hash of the preceding block.
     *
     * @return non-null hex-encoded hash string
     */
    public String getPreviousHash() {
        return previousHash;
    }

    /**
     * Returns the pre-computed SHA-256 hash of this block.
     *
     * @return non-null hex-encoded hash string
     */
    public String getHash() {
        return hash;
    }

    /**
     * Returns the ordered, immutable list of transactions in this block.
     *
     * @return non-null, unmodifiable list (maybe empty)
     */
    public List<Transaction> getTransactions() {
        return transactions;
    }

    /**
     * Convenience accessor for the Merkle root stored in the header.
     *
     * @return hex-encoded Merkle root string
     */
    public String getMerkleRoot() {
        return header.merkleRoot();
    }

    // ─── Object overrides ─────────────────────────────────────────────────────

    /**
     * Verifies that the stored {@link #hash} matches a fresh computation.
     *
     * <p>Used by {@link com.privatechain.core.builder.Blockchain#isChainValid()} and
     * storage implementations after deserialization to detect corruption (NFR-SEC-03).</p>
     *
     * @return {@code true} if the stored hash equals {@link #computeHash(int, String, BlockHeader)}
     */
    public boolean isHashValid() {
        return hash.equals(computeHash(index, previousHash, header));
    }

    /**
     * Two blocks are equal if and only if their hashes are equal.
     *
     * @param obj the object to compare
     * @return {@code true} if {@code obj} is a {@code Block} with the same hash
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Block other)) {
            return false;
        }
        return Objects.equals(hash, other.hash);
    }

    /**
     * Hash code based on the block's SHA-256 hash string.
     *
     * @return integer hash code consistent with {@link #equals(Object)}
     */
    @Override
    public int hashCode() {
        return Objects.hash(hash);
    }

    // ─── Builder ──────────────────────────────────────────────────────────────

    /**
     * Returns a human-readable summary of this block (safe for logging).
     *
     * @return string representation
     */
    @Override
    public String toString() {
        return "Block{"
            + "index=" + index
            + ", hash=" + hash.substring(0, Math.min(16, hash.length())) + "..."
            + ", previousHash=" + previousHash.substring(0, Math.min(16, previousHash.length())) + "..."
            + ", txCount=" + transactions.size()
            + ", timestamp=" + header.timestamp()
            + '}';
    }

    /**
     * Fluent builder for {@link Block}.
     *
     * <p>Calling {@link #build()} computes the block hash automatically from the
     * provided fields. There is no need to supply a hash explicitly.</p>
     *
     * <pre>{@code
     * Block block = Block.builder()
     *     .index(1)
     *     .previousHash(prev.getHash())
     *     .transactions(txList)
     *     .header(BlockHeader.builder().nonce(nonce).merkleRoot(root).build())
     *     .build();
     * }</pre>
     */
    public static final class Builder {

        private int index = 0;
        private BlockHeader header = BlockHeader.genesis();
        private String previousHash = GENESIS_PREVIOUS_HASH;
        private List<Transaction> transactions = new ArrayList<>();

        /**
         * Private constructor — use {@link Block#builder()}.
         */
        private Builder() {
        }

        /**
         * Sets the block index.
         *
         * @param index block index (&ge; 0)
         * @return this builder
         */
        public Builder index(int index) {
            this.index = index;
            return this;
        }

        /**
         * Sets the block header.
         *
         * @param header non-null block header
         * @return this builder
         */
        public Builder header(BlockHeader header) {
            this.header = Objects.requireNonNull(header, "header must not be null");
            return this;
        }

        /**
         * Sets the hash of the preceding block.
         *
         * @param previousHash hex-encoded SHA-256 hash (non-null)
         * @return this builder
         */
        public Builder previousHash(String previousHash) {
            this.previousHash = Objects.requireNonNull(previousHash, "previousHash must not be null");
            return this;
        }

        /**
         * Sets the transaction list for this block.
         *
         * @param transactions list of transactions (non-null; may be empty)
         * @return this builder
         */
        public Builder transactions(List<Transaction> transactions) {
            this.transactions = new ArrayList<>(
                Objects.requireNonNull(transactions, "transactions must not be null"));
            return this;
        }

        /**
         * Builds and returns an immutable {@link Block}.
         *
         * <p>The block hash is computed automatically from the supplied fields.
         * The builder may be reused after calling {@code build()}, but callers
         * should be aware that subsequent calls return a new {@code Block} with a
         * freshly computed hash (which reflects any field changes made in between).</p>
         *
         * @return a new immutable {@code Block} with a computed hash
         */
        public Block build() {
            String computedHash = Block.computeHash(index, previousHash, header);
            return new Block(index, header, previousHash, computedHash, transactions);
        }
    }
}
