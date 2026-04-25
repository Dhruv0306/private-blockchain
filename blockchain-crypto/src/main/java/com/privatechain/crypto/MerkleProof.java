package com.privatechain.crypto;

import java.util.Collections;
import java.util.List;

/**
 * An immutable Merkle inclusion proof.
 *
 * <p>A {@code MerkleProof} contains everything a verifier needs to confirm that a
 * specific transaction hash is included in a block, without possessing the full
 * block or rebuilding the entire Merkle tree.
 *
 * <p>Produced by {@link MerkleTree#getProof(String)}.
 * Verified by {@link MerkleTree#verify(String, MerkleProof, String)}.
 *
 * @param leafHash      the SHA-256 hash of the transaction being proved
 * @param siblingHashes the sibling hash at each level of the tree (leaf to root),
 *                      ordered bottom-up
 * @param isLeftSibling {@code true} at position {@code i} means the sibling at
 *                      level {@code i} is to the LEFT of the current node;
 *                      {@code false} means it is to the RIGHT
 * @see MerkleTree
 */
public record MerkleProof(
    String leafHash,
    List<String> siblingHashes,
    List<Boolean> isLeftSibling) {

    /**
     * Compact constructor — validates invariants and defensively copies lists.
     *
     * @param leafHash      SHA-256 leaf hash; must not be {@code null}
     * @param siblingHashes sibling hash list; must not be {@code null}
     * @param isLeftSibling direction list; must have same size as {@code siblingHashes}
     * @throws IllegalArgumentException if any argument is {@code null} or sizes differ
     */
    public MerkleProof {
        if (leafHash == null) {
            throw new IllegalArgumentException("leafHash must not be null");
        }
        if (siblingHashes == null) {
            throw new IllegalArgumentException("siblingHashes must not be null");
        }
        if (isLeftSibling == null) {
            throw new IllegalArgumentException("isLeftSibling must not be null");
        }
        if (siblingHashes.size() != isLeftSibling.size()) {
            throw new IllegalArgumentException(
                "siblingHashes and isLeftSibling must have the same size. Got: "
                    + siblingHashes.size() + " vs " + isLeftSibling.size()
            );
        }
        // Defensive copies — caller cannot mutate the proof after construction
        siblingHashes = Collections.unmodifiableList(List.copyOf(siblingHashes));
        isLeftSibling = Collections.unmodifiableList(List.copyOf(isLeftSibling));
    }

    /**
     * Returns the number of levels in this proof (equals tree height minus one).
     *
     * <p>A proof for a single-element tree has 0 levels.
     *
     * @return number of sibling entries
     */
    public int depth() {
        return siblingHashes.size();
    }
}
