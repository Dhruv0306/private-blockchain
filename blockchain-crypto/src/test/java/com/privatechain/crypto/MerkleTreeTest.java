package com.privatechain.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link MerkleTree} and {@link MerkleProof}.
 *
 * <p>Root vectors for 1, 2, 3, and 4 leaves are pre-computed from
 * the implementation's SHA-256 chain so they serve as regression anchors.
 */
@DisplayName("MerkleTree")
class MerkleTreeTest {

    // Leaf hashes used across tests — sha256 of simple strings
    private static final String H0 = HashUtil.sha256("tx-0");
    private static final String H1 = HashUtil.sha256("tx-1");
    private static final String H2 = HashUtil.sha256("tx-2");
    private static final String H3 = HashUtil.sha256("tx-3");

    // ── Empty tree ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Empty tree")
    class EmptyTreeTests {

        @Test
        @DisplayName("root is EMPTY_ROOT (sha256 of empty string)")
        void emptyRoot() {
            final MerkleTree tree = new MerkleTree(List.of());
            assertEquals(MerkleTree.EMPTY_ROOT, tree.getRoot());
            assertEquals(HashUtil.sha256(""), tree.getRoot());
        }

        @Test
        @DisplayName("level count is 0")
        void zeroLevels() {
            assertEquals(0, new MerkleTree(List.of()).levelCount());
        }

        @Test
        @DisplayName("getProof on empty tree throws IllegalArgumentException")
        void proofOnEmptyThrows() {
            final MerkleTree tree = new MerkleTree(List.of());
            assertThrows(IllegalArgumentException.class, () -> tree.getProof(H0));
        }

        @Test
        @DisplayName("null txHashes throws IllegalArgumentException")
        void nullInputThrows() {
            assertThrows(IllegalArgumentException.class, () -> new MerkleTree(null));
        }
    }

    // ── Single leaf ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Single leaf")
    class SingleLeafTests {

        @Test
        @DisplayName("root equals the single leaf hash")
        void rootEqualsLeaf() {
            final MerkleTree tree = new MerkleTree(List.of(H0));
            assertEquals(H0, tree.getRoot());
        }

        @Test
        @DisplayName("level count is 1")
        void oneLevelCount() {
            assertEquals(1, new MerkleTree(List.of(H0)).levelCount());
        }

        @Test
        @DisplayName("proof for the single leaf has 0 siblings")
        void proofHasNoSiblings() {
            final MerkleTree tree = new MerkleTree(List.of(H0));
            final MerkleProof proof = tree.getProof(H0);
            assertEquals(0, proof.depth());
        }

        @Test
        @DisplayName("single-leaf proof verifies correctly")
        void singleLeafProofVerifies() {
            final MerkleTree tree = new MerkleTree(List.of(H0));
            final MerkleProof proof = tree.getProof(H0);
            assertTrue(MerkleTree.verify(H0, proof, tree.getRoot()));
        }
    }

    // ── Two leaves ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Two leaves")
    class TwoLeafTests {

        @Test
        @DisplayName("root = sha256(H0 + H1)")
        void rootIsCombinedHash() {
            final MerkleTree tree = new MerkleTree(List.of(H0, H1));
            final String expected = HashUtil.sha256(H0 + H1);
            assertEquals(expected, tree.getRoot());
        }

        @Test
        @DisplayName("level count is 2 (leaves + root)")
        void twoLevels() {
            assertEquals(2, new MerkleTree(List.of(H0, H1)).levelCount());
        }

        @Test
        @DisplayName("proof for H0 has sibling H1")
        void proofForLeftLeaf() {
            final MerkleTree tree = new MerkleTree(List.of(H0, H1));
            final MerkleProof proof = tree.getProof(H0);
            assertEquals(1, proof.depth());
            assertEquals(H1, proof.siblingHashes().get(0));
            assertFalse(proof.isLeftSibling().get(0), "H1 should be RIGHT sibling of H0");
        }

        @Test
        @DisplayName("proof for H1 has sibling H0")
        void proofForRightLeaf() {
            final MerkleTree tree = new MerkleTree(List.of(H0, H1));
            final MerkleProof proof = tree.getProof(H1);
            assertEquals(1, proof.depth());
            assertEquals(H0, proof.siblingHashes().get(0));
            assertTrue(proof.isLeftSibling().get(0), "H0 should be LEFT sibling of H1");
        }

        @Test
        @DisplayName("both proofs verify against the same root")
        void bothProofsVerify() {
            final MerkleTree tree = new MerkleTree(List.of(H0, H1));
            final String root = tree.getRoot();
            assertTrue(MerkleTree.verify(H0, tree.getProof(H0), root));
            assertTrue(MerkleTree.verify(H1, tree.getProof(H1), root));
        }
    }

    // ── Three leaves (odd count) ──────────────────────────────────────────────

    @Nested
    @DisplayName("Three leaves (odd count — last duplicated)")
    class ThreeLeafTests {

        @Test
        @DisplayName("root matches expected value for 3-leaf tree")
        void rootMatchesExpected() {
            // Level 0: [H0, H1, H2]
            // Level 1: [sha256(H0+H1), sha256(H2+H2)]   <- H2 duplicated
            // Level 2: [sha256(level1[0]+level1[1])]
            final String l1left  = HashUtil.sha256(H0 + H1);
            final String l1right = HashUtil.sha256(H2 + H2);
            final String root    = HashUtil.sha256(l1left + l1right);

            final MerkleTree tree = new MerkleTree(List.of(H0, H1, H2));
            assertEquals(root, tree.getRoot());
        }

        @Test
        @DisplayName("all three leaf proofs verify")
        void allProofsVerify() {
            final MerkleTree tree = new MerkleTree(List.of(H0, H1, H2));
            final String root = tree.getRoot();
            assertTrue(MerkleTree.verify(H0, tree.getProof(H0), root));
            assertTrue(MerkleTree.verify(H1, tree.getProof(H1), root));
            assertTrue(MerkleTree.verify(H2, tree.getProof(H2), root));
        }

        @Test
        @DisplayName("level count is 3")
        void threeLevels() {
            assertEquals(3, new MerkleTree(List.of(H0, H1, H2)).levelCount());
        }
    }

    // ── Four leaves ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Four leaves (balanced)")
    class FourLeafTests {

        @Test
        @DisplayName("root matches expected value for balanced 4-leaf tree")
        void rootMatchesExpected() {
            // Level 0: [H0, H1, H2, H3]
            // Level 1: [sha256(H0+H1), sha256(H2+H3)]
            // Level 2: [sha256(level1[0]+level1[1])]
            final String l1left  = HashUtil.sha256(H0 + H1);
            final String l1right = HashUtil.sha256(H2 + H3);
            final String root    = HashUtil.sha256(l1left + l1right);

            final MerkleTree tree = new MerkleTree(List.of(H0, H1, H2, H3));
            assertEquals(root, tree.getRoot());
        }

        @Test
        @DisplayName("all four proofs verify against root")
        void allProofsVerify() {
            final MerkleTree tree = new MerkleTree(List.of(H0, H1, H2, H3));
            final String root = tree.getRoot();
            assertTrue(MerkleTree.verify(H0, tree.getProof(H0), root));
            assertTrue(MerkleTree.verify(H1, tree.getProof(H1), root));
            assertTrue(MerkleTree.verify(H2, tree.getProof(H2), root));
            assertTrue(MerkleTree.verify(H3, tree.getProof(H3), root));
        }

        @Test
        @DisplayName("level count is 3 (leaves + 2 internal)")
        void threeLevels() {
            assertEquals(3, new MerkleTree(List.of(H0, H1, H2, H3)).levelCount());
        }
    }

    // ── Proof security ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Proof security")
    class ProofSecurityTests {

        @Test
        @DisplayName("proof for wrong leaf does not verify against root")
        void wrongLeafFails() {
            final MerkleTree tree = new MerkleTree(List.of(H0, H1, H2, H3));
            final MerkleProof proofForH0 = tree.getProof(H0);
            // Use H1's hash with H0's proof — must fail
            assertFalse(MerkleTree.verify(H1, proofForH0, tree.getRoot()));
        }

        @Test
        @DisplayName("tampered sibling hash fails verification")
        void tamperedSiblingFails() {
            final MerkleTree tree = new MerkleTree(List.of(H0, H1, H2, H3));
            final MerkleProof original = tree.getProof(H0);
            // Replace first sibling with a different hash
            final List<String> tamperedSiblings = new java.util.ArrayList<>(original.siblingHashes());
            tamperedSiblings.set(0, HashUtil.sha256("tampered"));
            final MerkleProof tampered = new MerkleProof(
                original.leafHash(), tamperedSiblings, original.isLeftSibling());
            assertFalse(MerkleTree.verify(H0, tampered, tree.getRoot()));
        }

        @Test
        @DisplayName("proof against wrong root fails")
        void wrongRootFails() {
            final MerkleTree tree = new MerkleTree(List.of(H0, H1, H2, H3));
            final MerkleProof proof = tree.getProof(H0);
            assertFalse(MerkleTree.verify(H0, proof, HashUtil.sha256("wrong-root")));
        }

        @Test
        @DisplayName("different tx sets produce different roots")
        void differentSetsProduceDifferentRoots() {
            final MerkleTree tree1 = new MerkleTree(List.of(H0, H1));
            final MerkleTree tree2 = new MerkleTree(List.of(H0, H2));
            assertNotEquals(tree1.getRoot(), tree2.getRoot());
        }

        @Test
        @DisplayName("getProof throws for unknown leaf hash")
        void unknownLeafThrows() {
            final MerkleTree tree = new MerkleTree(List.of(H0, H1));
            assertThrows(IllegalArgumentException.class,
                () -> tree.getProof(HashUtil.sha256("not-in-tree")));
        }
    }

    // ── verify() null checks ──────────────────────────────────────────────────

    @Nested
    @DisplayName("verify() null checks")
    class VerifyNullTests {

        @Test
        @DisplayName("null leafHash throws IllegalArgumentException")
        void nullLeafHashThrows() {
            final MerkleTree tree = new MerkleTree(List.of(H0));
            final MerkleProof proof = tree.getProof(H0);
            assertThrows(IllegalArgumentException.class,
                () -> MerkleTree.verify(null, proof, tree.getRoot()));
        }

        @Test
        @DisplayName("null proof throws IllegalArgumentException")
        void nullProofThrows() {
            final MerkleTree tree = new MerkleTree(List.of(H0));
            assertThrows(IllegalArgumentException.class,
                () -> MerkleTree.verify(H0, null, tree.getRoot()));
        }

        @Test
        @DisplayName("null root throws IllegalArgumentException")
        void nullRootThrows() {
            final MerkleTree tree = new MerkleTree(List.of(H0));
            final MerkleProof proof = tree.getProof(H0);
            assertThrows(IllegalArgumentException.class,
                () -> MerkleTree.verify(H0, proof, null));
        }
    }

    // ── MerkleProof ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("MerkleProof")
    class MerkleProofTests {

        @Test
        @DisplayName("depth() returns number of siblings")
        void depthMatchesSiblingCount() {
            final MerkleTree tree = new MerkleTree(List.of(H0, H1, H2, H3));
            final MerkleProof proof = tree.getProof(H0);
            assertEquals(proof.siblingHashes().size(), proof.depth());
        }

        @Test
        @DisplayName("siblingHashes is unmodifiable")
        void siblingHashesUnmodifiable() {
            final MerkleTree tree = new MerkleTree(List.of(H0, H1));
            final MerkleProof proof = tree.getProof(H0);
            assertThrows(UnsupportedOperationException.class,
                () -> proof.siblingHashes().add("extra"));
        }

        @Test
        @DisplayName("isLeftSibling is unmodifiable")
        void isLeftSiblingUnmodifiable() {
            final MerkleTree tree = new MerkleTree(List.of(H0, H1));
            final MerkleProof proof = tree.getProof(H0);
            assertThrows(UnsupportedOperationException.class,
                () -> proof.isLeftSibling().add(true));
        }

        @Test
        @DisplayName("mismatched list sizes throw IllegalArgumentException")
        void mismatchedSizesThrow() {
            assertThrows(IllegalArgumentException.class, () ->
                new MerkleProof(H0, List.of(H1, H2), List.of(true)));
        }
    }
}
