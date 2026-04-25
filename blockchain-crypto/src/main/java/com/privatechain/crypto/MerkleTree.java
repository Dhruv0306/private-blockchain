package com.privatechain.crypto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builds a binary Merkle tree from a list of transaction ID hashes and
 * produces the Merkle root and inclusion proofs.
 *
 * <p>A Merkle tree is a binary hash tree where:
 * <ul>
 *   <li><strong>Leaf nodes</strong> are the SHA-256 hashes of individual transaction IDs.</li>
 *   <li><strong>Internal nodes</strong> are the SHA-256 hash of the concatenation of their
 *       two child hashes (left + right).</li>
 *   <li>If a level has an odd number of nodes, the last node is <em>duplicated</em>
 *       (Bitcoin convention) to make it even before hashing upward.</li>
 * </ul>
 *
 * <p>The root hash uniquely represents the entire set of transactions. A single-bit
 * change in any transaction ID produces a completely different root.
 *
 * <p>Usage:
 * <pre>{@code
 *   List<String> txIds = List.of(
 *       HashUtil.sha256("tx-1"),
 *       HashUtil.sha256("tx-2"),
 *       HashUtil.sha256("tx-3")
 *   );
 *   MerkleTree tree = new MerkleTree(txIds);
 *   String root = tree.getRoot();
 *   MerkleProof proof = tree.getProof(txIds.get(0));
 *   boolean valid = MerkleTree.verify(txIds.get(0), proof, root); // true
 * }</pre>
 *
 * @see MerkleProof
 * @see HashUtil
 */
public final class MerkleTree {

    /**
     * The Merkle root returned when the tree is built from an empty transaction list.
     * Defined as the SHA-256 hash of an empty string.
     */
    public static final String EMPTY_ROOT = HashUtil.sha256("");

    /** All levels of the tree, index 0 = leaf level, last index = root level. */
    private final List<List<String>> levels;

    /** The original (possibly padded) leaf hashes. */
    private final List<String> leaves;

    /**
     * Constructs a Merkle tree from a list of transaction ID hashes.
     *
     * <p>The caller is responsible for providing pre-hashed leaf values.
     * Typically, each element is {@code HashUtil.sha256(transactionId)}.
     *
     * <p>Edge cases:
     * <ul>
     *   <li><strong>Empty list</strong>: root is {@link #EMPTY_ROOT}; no proofs can be generated.</li>
     *   <li><strong>Single element</strong>: root equals that element; proof has no siblings.</li>
     *   <li><strong>Odd count</strong>: last leaf is duplicated before hashing upward.</li>
     * </ul>
     *
     * @param txHashes SHA-256 hashes of transaction IDs; must not be {@code null};
     *                 each element must be a 64-character lowercase hex string
     * @throws IllegalArgumentException if {@code txHashes} is {@code null}
     */
    public MerkleTree(final List<String> txHashes) {
        if (txHashes == null) {
            throw new IllegalArgumentException("txHashes must not be null");
        }
        this.levels = new ArrayList<>();
        this.leaves = new ArrayList<>();

        if (txHashes.isEmpty()) {
            // Empty tree — no levels to build
            return;
        }

        buildTree(txHashes);
    }

    /**
     * Returns the Merkle root hash.
     *
     * <p>For an empty tree, returns {@link #EMPTY_ROOT}.
     *
     * @return 64-character lowercase hex Merkle root
     */
    public String getRoot() {
        if (levels.isEmpty()) {
            return EMPTY_ROOT;
        }
        final List<String> rootLevel = levels.get(levels.size() - 1);
        return rootLevel.get(0);
    }

    /**
     * Generates a {@link MerkleProof} for the given leaf hash.
     *
     * <p>The proof contains the sibling hashes needed to recompute the root from
     * the leaf — one sibling per tree level. A verifier needs only the leaf hash,
     * the proof, and the root to confirm inclusion without downloading all transactions.
     *
     * @param leafHash the SHA-256 hash of a transaction ID to prove; must not be {@code null}
     * @return a proof that the leaf is included in this tree
     * @throws IllegalArgumentException if {@code leafHash} is {@code null} or not in the tree
     */
    public MerkleProof getProof(final String leafHash) {
        if (leafHash == null) {
            throw new IllegalArgumentException("leafHash must not be null");
        }
        if (levels.isEmpty()) {
            throw new IllegalArgumentException("Cannot generate proof from an empty tree");
        }

        final int leafIndex = leaves.indexOf(leafHash);
        if (leafIndex == -1) {
            throw new IllegalArgumentException(
                "Leaf hash not found in tree: " + leafHash.substring(0, Math.min(16, leafHash.length())) + "..."
            );
        }

        final List<String> siblingHashes    = new ArrayList<>();
        final List<Boolean> isLeftSibling   = new ArrayList<>();
        int currentIndex = leafIndex;

        // Walk up from leaf level to one below root
        for (int level = 0; level < levels.size() - 1; level++) {
            final List<String> currentLevel = levels.get(level);
            final boolean isRightChild = ((currentIndex & 1) == 1);
            final int siblingIndex = isRightChild ? currentIndex - 1 : currentIndex + 1;

            // Handle odd-count levels: last node was duplicated, sibling = itself
            final String sibling = siblingIndex < currentLevel.size()
                ? currentLevel.get(siblingIndex)
                : currentLevel.get(currentIndex); // duplicated leaf case

            siblingHashes.add(sibling);
            isLeftSibling.add(isRightChild); // sibling is LEFT when we are RIGHT
            currentIndex = currentIndex / 2;
        }

        return new MerkleProof(leafHash, siblingHashes, isLeftSibling);
    }

    /**
     * Verifies that a {@link MerkleProof} is valid for the given leaf hash and Merkle root.
     *
     * <p>This is a pure static utility — it requires no tree instance.
     * A third party can call this method to verify inclusion without
     * possessing or rebuilding the full tree.
     *
     * @param leafHash  the SHA-256 hash of the transaction to verify; must not be {@code null}
     * @param proof     the inclusion proof; must not be {@code null}
     * @param merkleRoot the expected Merkle root; must not be {@code null}
     * @return {@code true} if the proof correctly reconstructs {@code merkleRoot}
     *         from {@code leafHash}; {@code false} otherwise
     * @throws IllegalArgumentException if any argument is {@code null}
     */
    public static boolean verify(
        final String leafHash,
        final MerkleProof proof,
        final String merkleRoot) {
        if (leafHash == null) {
            throw new IllegalArgumentException("leafHash must not be null");
        }
        if (proof == null) {
            throw new IllegalArgumentException("proof must not be null");
        }
        if (merkleRoot == null) {
            throw new IllegalArgumentException("merkleRoot must not be null");
        }

        String computed = leafHash;
        final List<String> siblings      = proof.siblingHashes();
        final List<Boolean> leftSiblings = proof.isLeftSibling();

        for (int i = 0; i < siblings.size(); i++) {
            final String sibling = siblings.get(i);
            final boolean siblingIsLeft = leftSiblings.get(i);
            computed = siblingIsLeft
                ? combineHashes(sibling, computed)   // sibling LEFT, current RIGHT
                : combineHashes(computed, sibling);  // current LEFT, sibling RIGHT
        }

        return computed.equals(merkleRoot);
    }

    /**
     * Returns the number of levels in the tree (including the leaf level and root level).
     *
     * <p>An empty tree has 0 levels. A tree with one leaf has 1 level.
     *
     * @return number of levels
     */
    public int levelCount() {
        return levels.size();
    }

    // ── private tree-building helpers ─────────────────────────────────────────

    private void buildTree(final List<String> txHashes) {
        // Start with the leaf level
        final List<String> leafLevel = new ArrayList<>(txHashes);
        levels.add(Collections.unmodifiableList(new ArrayList<>(leafLevel)));
        leaves.addAll(leafLevel);

        List<String> currentLevel = leafLevel;

        // Build each level upward until we reach the root (single element)
        while (currentLevel.size() > 1) {
            currentLevel = buildNextLevel(currentLevel);
            levels.add(Collections.unmodifiableList(new ArrayList<>(currentLevel)));
        }
    }

    private static List<String> buildNextLevel(final List<String> currentLevel) {
        final List<String> nextLevel = new ArrayList<>();
        final int size = currentLevel.size();

        for (int i = 0; i < size; i += 2) {
            final String left  = currentLevel.get(i);
            // If no right sibling: duplicate left (Bitcoin convention for odd-count levels)
            final String right = (i + 1 < size) ? currentLevel.get(i + 1) : left;
            nextLevel.add(combineHashes(left, right));
        }

        return nextLevel;
    }

    private static String combineHashes(final String left, final String right) {
        return HashUtil.sha256(left + right);
    }
}
