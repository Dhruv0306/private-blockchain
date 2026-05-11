package com.privatechain.crypto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Builds a binary Merkle tree from a list of transaction IDs and exposes
 * root computation, inclusion-proof generation, and proof verification.
 *
 * <p>A Merkle tree provides a compact cryptographic commitment to an ordered
 * set of transactions. Given only the 32-byte root, any third party can verify
 * that a specific transaction is included in the set without downloading all
 * transactions (FR-CRYPTO-06, FR-CRYPTO-07).</p>
 *
 * <h2>Hash function</h2>
 * Each node stores {@code SHA-256(left || right)} (concatenation of lowercase
 * hex strings). Leaf nodes store {@code SHA-256(txId)}. This is consistent with
 * the convention used in Bitcoin.
 *
 * <h2>Odd-node handling</h2>
 * When a level has an odd number of nodes, the last node is duplicated
 * (Bitcoin / BIP-34 convention) to form a complete binary tree.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * List<String> txIds = block.getTransactions()
 *                           .stream()
 *                           .map(tx -> tx.getId().toString())
 *                           .collect(Collectors.toList());
 *
 * String root  = MerkleTree.buildRoot(txIds);
 * MerkleProof p = MerkleTree.getProof(txIds, targetTxId);
 * boolean ok   = MerkleTree.verifyProof(p, root, targetTxId);
 * }</pre>
 *
 * @see MerkleProof
 * @see HashUtil
 * @since 1.0.0
 */
public final class MerkleTree {

    /**
     * Canonical root returned when the transaction list is empty.
     * This is the same 64-zero sentinel used for the genesis block's Merkle root.
     */
    public static final String EMPTY_ROOT = "0".repeat(64);

    /**
     * Utility class — no instances.
     */
    private MerkleTree() {
        throw new UnsupportedOperationException("MerkleTree is a utility class");
    }

    // ─── Root computation ─────────────────────────────────────────────────────

    /**
     * Builds the Merkle root of an ordered list of transaction IDs.
     *
     * <p>Returns {@link #EMPTY_ROOT} when the list is empty (genesis block behavior).
     * The root changes deterministically if any transaction ID changes or the order
     * changes, providing tamper evidence for the entire transaction set.</p>
     *
     * @param txIds ordered list of transaction ID strings (non-null; may be empty)
     * @return 64-character lowercase hex Merkle root
     * @throws NullPointerException     if txIds is null or any element is null
     * @throws IllegalArgumentException if any txId is blank
     */
    public static String buildRoot(List<String> txIds) {
        Objects.requireNonNull(txIds, "txIds must not be null");

        if (txIds.isEmpty()) {
            return EMPTY_ROOT;
        }

        // Validate each ID
        for (String txId : txIds) {
            Objects.requireNonNull(txId, "txId element must not be null");
            if (txId.isBlank()) {
                throw new IllegalArgumentException("txId must not be blank");
            }
        }

        // Layer 0: hash each transaction ID individually
        List<String> currentLayer = new ArrayList<>(txIds.size());
        for (String txId : txIds) {
            currentLayer.add(HashUtil.sha256(txId));
        }

        // Reduce layers until only the root remains
        while (currentLayer.size() > 1) {
            currentLayer = buildNextLayer(currentLayer);
        }

        return currentLayer.get(0);
    }

    // ─── Proof generation ─────────────────────────────────────────────────────

    /**
     * Generates a Merkle inclusion proof for the given transaction ID.
     *
     * <p>The proof is a list of (sibling hash, position) pairs that, together with
     * the leaf hash of {@code targetTxId}, allow reconstruction of the Merkle root
     * without knowing any other transaction in the block.</p>
     *
     * @param txIds      the complete, ordered list of transaction IDs in the block (non-null)
     * @param targetTxId the transaction ID for which to generate the proof (non-null, non-blank)
     * @return a {@link MerkleProof} containing the sibling path; never null
     * @throws NullPointerException     if txIds or targetTxId is null
     * @throws IllegalArgumentException if targetTxId is blank or not found in txIds
     */
    public static MerkleProof getProof(List<String> txIds, String targetTxId) {
        Objects.requireNonNull(txIds, "txIds must not be null");
        Objects.requireNonNull(targetTxId, "targetTxId must not be null");
        if (targetTxId.isBlank()) {
            throw new IllegalArgumentException("targetTxId must not be blank");
        }

        // Find target index
        int targetIndex = txIds.indexOf(targetTxId);
        if (targetIndex < 0) {
            throw new IllegalArgumentException(
                "targetTxId '" + targetTxId + "' not found in transaction list");
        }

        List<MerkleProof.ProofNode> path = new ArrayList<>();

        // Build layers, tracking the target's index as we go up
        List<String> currentLayer = new ArrayList<>(txIds.size());
        for (String txId : txIds) {
            currentLayer.add(HashUtil.sha256(txId));
        }

        int currentIndex = targetIndex;

        while (currentLayer.size() > 1) {
            // Determine sibling index and position.
            // Use bitwise AND instead of modulo: (n % 2 == 1) is wrong for negative n,
            // whereas (n & 1) == 1 is always correct (SpotBugs IM_BAD_CHECK_FOR_ODD).
            boolean isRightChild = (currentIndex & 1) == 1;
            int siblingIndex = isRightChild ? currentIndex - 1 : currentIndex + 1;

            // If sibling is out of bounds (odd last node), duplicate the current node
            if (siblingIndex >= currentLayer.size()) {
                siblingIndex = currentIndex;
            }

            String siblingHash = currentLayer.get(siblingIndex);
            // isLeft indicates the sibling is on the LEFT of the current node
            path.add(new MerkleProof.ProofNode(siblingHash, isRightChild));

            // Advance to next layer
            currentLayer = buildNextLayer(currentLayer);
            currentIndex = currentIndex / 2;
        }

        return new MerkleProof(targetTxId, Collections.unmodifiableList(path));
    }

    // ─── Proof verification ───────────────────────────────────────────────────

    /**
     * Verifies that a {@link MerkleProof} establishes inclusion of a transaction
     * in a block whose Merkle root is {@code expectedRoot}.
     *
     * <p>Only the proof and the expected root are required — no other transaction
     * data is needed. This allows lightweight clients to verify inclusion without
     * downloading the full block.</p>
     *
     * @param proof        the proof returned by {@link #getProof} (non-null)
     * @param expectedRoot the Merkle root stored in the block header (non-null, non-blank)
     * @param txId         the transaction ID whose inclusion is being proven (non-null, non-blank)
     * @return {@code true} if the proof is valid and {@code txId} is in the block;
     * {@code false} otherwise
     * @throws NullPointerException     if proof, expectedRoot, or txId is null
     * @throws IllegalArgumentException if expectedRoot or txId is blank
     */
    public static boolean verifyProof(MerkleProof proof, String expectedRoot, String txId) {
        Objects.requireNonNull(proof, "proof must not be null");
        Objects.requireNonNull(expectedRoot, "expectedRoot must not be null");
        Objects.requireNonNull(txId, "txId must not be null");
        if (expectedRoot.isBlank()) {
            throw new IllegalArgumentException("expectedRoot must not be blank");
        }
        if (txId.isBlank()) {
            throw new IllegalArgumentException("txId must not be blank");
        }

        // The proof was generated for a different tx — immediate failure
        if (!txId.equals(proof.getTransactionId())) {
            return false;
        }

        // Start from the leaf hash of the target tx
        String computedHash = HashUtil.sha256(txId);

        // Walk the proof path from leaf to root
        for (MerkleProof.ProofNode node : proof.getPath()) {
            if (node.isSiblingOnLeft()) {
                // Sibling is to our left: hash = SHA-256(sibling || current)
                computedHash = HashUtil.sha256(node.getSiblingHash() + computedHash);
            } else {
                // Sibling is to our right: hash = SHA-256(current || sibling)
                computedHash = HashUtil.sha256(computedHash + node.getSiblingHash());
            }
        }

        // Compare computed root to expected root using a constant-time byte comparison
        // to prevent timing-based side-channel attacks (SpotBugs UNSAFE_HASH_EQUALS).
        byte[] computedBytes = computedHash.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] expectedBytes = expectedRoot.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(computedBytes, expectedBytes);
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    /**
     * Reduces a layer of hashes to the next layer by combining adjacent pairs.
     *
     * <p>When the layer has an odd number of nodes, the last node is paired with
     * itself (duplicated) before hashing — Bitcoin/BIP-34 convention.</p>
     *
     * @param layer the current layer of hex-encoded hashes
     * @return the next (parent) layer, with half as many nodes (rounded up)
     */
    private static List<String> buildNextLayer(List<String> layer) {
        List<String> parent = new ArrayList<>((layer.size() + 1) / 2);

        for (int i = 0; i < layer.size(); i += 2) {
            String left = layer.get(i);
            // Duplicate last node when count is odd
            String right = (i + 1 < layer.size()) ? layer.get(i + 1) : left;
            parent.add(HashUtil.sha256(left + right));
        }

        return parent;
    }

    // ─── Nested value types ───────────────────────────────────────────────────

    /**
     * Immutable Merkle inclusion proof for a single transaction.
     *
     * <p>A proof consists of an ordered list of {@link ProofNode} elements, one per
     * tree level from the leaf up to (but not including) the root. Each node provides
     * the sibling hash and whether the sibling is the left child at that level
     * (FR-CRYPTO-07).</p>
     *
     * @since 1.0.0
     */
    public static final class MerkleProof {

        private final String transactionId;
        private final List<ProofNode> path;

        /**
         * Constructs a Merkle proof.
         *
         * @param transactionId the transaction this proof covers (non-null, non-blank)
         * @param path          immutable list of proof nodes from leaf to root (non-null)
         * @throws NullPointerException     if transactionId or path is null
         * @throws IllegalArgumentException if transactionId is blank
         */
        public MerkleProof(String transactionId, List<ProofNode> path) {
            Objects.requireNonNull(transactionId, "transactionId must not be null");
            Objects.requireNonNull(path, "path must not be null");
            if (transactionId.isBlank()) {
                throw new IllegalArgumentException("transactionId must not be blank");
            }
            this.transactionId = transactionId;
            this.path = Collections.unmodifiableList(new ArrayList<>(path));
        }

        /**
         * Returns the transaction ID this proof covers.
         *
         * @return non-null, non-blank transaction ID
         */
        public String getTransactionId() {
            return transactionId;
        }

        /**
         * Returns the ordered sibling path from leaf to root.
         *
         * @return unmodifiable list of {@link ProofNode}
         */
        public List<ProofNode> getPath() {
            return path;
        }

        /**
         * Returns the depth of the tree from which this proof was generated,
         * which equals the number of sibling hashes in the path.
         *
         * @return tree depth (0 for a single-transaction block)
         */
        public int getDepth() {
            return path.size();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "MerkleProof{txId=" + transactionId + ", depth=" + path.size() + '}';
        }

        /**
         * Immutable value holding a sibling hash and its relative position at one
         * level of the Merkle tree.
         *
         * @since 1.0.0
         */
        public static final class ProofNode {

            private final String siblingHash;
            /**
             * {@code true} if the sibling is on the LEFT side (the current node is on the right).
             * {@code false} means the sibling is on the RIGHT (current node is on the left).
             */
            private final boolean siblingOnLeft;

            /**
             * Constructs a {@code ProofNode}.
             *
             * @param siblingHash   the sibling's hash (non-null, non-blank)
             * @param siblingOnLeft {@code true} if the sibling is to the left of the current node
             * @throws NullPointerException     if siblingHash is null
             * @throws IllegalArgumentException if siblingHash is blank
             */
            public ProofNode(String siblingHash, boolean siblingOnLeft) {
                Objects.requireNonNull(siblingHash, "siblingHash must not be null");
                if (siblingHash.isBlank()) {
                    throw new IllegalArgumentException("siblingHash must not be blank");
                }
                this.siblingHash = siblingHash;
                this.siblingOnLeft = siblingOnLeft;
            }

            /**
             * Returns the sibling's hash at this level of the tree.
             *
             * @return non-null, non-blank hex hash string
             */
            public String getSiblingHash() {
                return siblingHash;
            }

            /**
             * Returns {@code true} if the sibling is to the left of the current node
             * (meaning the current node is a right child at this level).
             *
             * @return sibling position flag
             */
            public boolean isSiblingOnLeft() {
                return siblingOnLeft;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String toString() {
                return "ProofNode{siblingHash=" + siblingHash.substring(0, 8)
                    + "..., siblingOnLeft=" + siblingOnLeft + '}';
            }
        }
    }
}
