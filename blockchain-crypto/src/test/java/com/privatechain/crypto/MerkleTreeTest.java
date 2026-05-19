package com.privatechain.crypto;

import com.privatechain.crypto.MerkleTree.MerkleProof;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MerkleTree}, {@link MerkleProof}, and proof verification.
 *
 * <p>Tests cover:</p>
 * <ul>
 *   <li>Empty and single-element edge cases</li>
 *   <li>Even and odd transaction counts (odd-node duplication)</li>
 *   <li>Root determinism and tamper-detection</li>
 *   <li>Proof generation and round-trip verification</li>
 *   <li>Proof verification with tampered data</li>
 *   <li>Null-argument guards</li>
 * </ul>
 */
@DisplayName("MerkleTree")
class MerkleTreeTest {

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Creates a deterministic list of tx IDs of the given length.
     */
    private List<String> txIds(int count) {
        List<String> ids = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add("tx-" + i + "-" + UUID.randomUUID());
        }
        return ids;
    }

    // ─── buildRoot ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("buildRoot()")
    class BuildRootTests {

        @Test
        @DisplayName("empty list returns EMPTY_ROOT sentinel")
        void emptyListReturnsEmptyRoot() {
            assertEquals(MerkleTree.EMPTY_ROOT, MerkleTree.buildRoot(List.of()));
        }

        @Test
        @DisplayName("EMPTY_ROOT is 64 zero-characters")
        void emptyRootIs64Zeros() {
            String er = MerkleTree.EMPTY_ROOT;
            assertEquals(64, er.length());
            assertTrue(er.chars().allMatch(c -> c == '0'));
        }

        @Test
        @DisplayName("single tx: root equals SHA-256 of that tx ID")
        void singleTxRoot() {
            String txId = "only-tx";
            String expected = HashUtil.sha256(txId);
            assertEquals(expected, MerkleTree.buildRoot(List.of(txId)));
        }

        @Test
        @DisplayName("root is 64-character hex string")
        void rootIs64CharHex() {
            String root = MerkleTree.buildRoot(txIds(4));
            assertEquals(64, root.length());
            assertTrue(root.matches("[0-9a-f]+"));
        }

        @Test
        @DisplayName("root is deterministic for the same input")
        void rootIsDeterministic() {
            List<String> ids = txIds(8);
            assertEquals(MerkleTree.buildRoot(ids), MerkleTree.buildRoot(ids));
        }

        @Test
        @DisplayName("changing any tx ID changes the root (tamper detection)")
        void changedTxIdChangesRoot() {
            List<String> original = new java.util.ArrayList<>(txIds(5));
            String rootBefore = MerkleTree.buildRoot(original);

            List<String> tampered = new java.util.ArrayList<>(original);
            tampered.set(2, "TAMPERED-TX-ID");
            String rootAfter = MerkleTree.buildRoot(tampered);

            assertNotEquals(rootBefore, rootAfter, "tampered tx must change the root");
        }

        @Test
        @DisplayName("reordering tx IDs changes the root")
        void reorderedTxsChangeRoot() {
            List<String> ids = new java.util.ArrayList<>(txIds(4));
            String root1 = MerkleTree.buildRoot(ids);

            java.util.Collections.swap(ids, 0, 1);
            String root2 = MerkleTree.buildRoot(ids);

            assertNotEquals(root1, root2, "tx order must affect the root");
        }

        @Test
        @DisplayName("odd number of txs builds successfully (odd-node duplication)")
        void oddTxCount() {
            // 3, 5, 7 are the key odd-count edge cases
            assertNotNull(MerkleTree.buildRoot(txIds(3)));
            assertNotNull(MerkleTree.buildRoot(txIds(5)));
            assertNotNull(MerkleTree.buildRoot(txIds(7)));
        }

        @Test
        @DisplayName("null tx list throws NullPointerException")
        void nullListThrows() {
            assertThrows(NullPointerException.class, () -> MerkleTree.buildRoot(null));
        }

        @Test
        @DisplayName("null tx ID element throws NullPointerException")
        void nullElementThrows() {
            assertThrows(NullPointerException.class,
                () -> MerkleTree.buildRoot(java.util.Arrays.asList("a", null, "b")));
        }

        @Test
        @DisplayName("blank tx ID element throws IllegalArgumentException")
        void blankElementThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> MerkleTree.buildRoot(List.of("ok", "  ", "alsoOk")));
        }
    }

    // ─── getProof ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getProof()")
    class GetProofTests {

        @Test
        @DisplayName("proof for the only tx in a single-tx block has empty path")
        void singleTxProofHasEmptyPath() {
            String txId = "sole-tx";
            MerkleProof proof = MerkleTree.getProof(List.of(txId), txId);
            assertEquals(txId, proof.getTransactionId());
            assertTrue(proof.getPath().isEmpty(), "single-tx tree has depth 0 — no siblings");
        }

        @Test
        @DisplayName("proof is not null for any tx in an even-count list")
        void proofNotNullEven() {
            List<String> ids = txIds(4);
            for (String id : ids) {
                assertNotNull(MerkleTree.getProof(ids, id));
            }
        }

        @Test
        @DisplayName("proof is not null for any tx in an odd-count list")
        void proofNotNullOdd() {
            List<String> ids = txIds(5);
            for (String id : ids) {
                assertNotNull(MerkleTree.getProof(ids, id));
            }
        }

        @Test
        @DisplayName("proof depth grows with tree height")
        void proofDepthGrowsWithSize() {
            List<String> two = txIds(2);
            List<String> four = txIds(4);
            List<String> eight = txIds(8);

            MerkleProof p2 = MerkleTree.getProof(two, two.get(0));
            MerkleProof p4 = MerkleTree.getProof(four, four.get(0));
            MerkleProof p8 = MerkleTree.getProof(eight, eight.get(0));

            assertTrue(p8.getDepth() > p4.getDepth(),
                "deeper tree should produce a longer proof");
            assertTrue(p4.getDepth() > p2.getDepth(),
                "8-element tree proof should be deeper than 4-element tree proof");
        }

        @Test
        @DisplayName("txId not in list throws IllegalArgumentException")
        void missingTxIdThrows() {
            List<String> ids = txIds(3);
            assertThrows(IllegalArgumentException.class,
                () -> MerkleTree.getProof(ids, "NONEXISTENT-TX"));
        }

        @Test
        @DisplayName("null txIds throws NullPointerException")
        void nullListThrows() {
            assertThrows(NullPointerException.class,
                () -> MerkleTree.getProof(null, "anything"));
        }

        @Test
        @DisplayName("null targetTxId throws NullPointerException")
        void nullTargetThrows() {
            List<String> ids = txIds(2);
            assertThrows(NullPointerException.class,
                () -> MerkleTree.getProof(ids, null));
        }

        @Test
        @DisplayName("blank targetTxId throws IllegalArgumentException")
        void blankTargetThrows() {
            List<String> ids = txIds(2);
            assertThrows(IllegalArgumentException.class,
                () -> MerkleTree.getProof(ids, "  "));
        }
    }

    // ─── verifyProof ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("verifyProof()")
    class VerifyProofTests {

        @Test
        @DisplayName("round-trip: proof verifies for every tx in a 2-element block")
        void roundTrip2() {
            List<String> ids = txIds(2);
            String root = MerkleTree.buildRoot(ids);
            for (String id : ids) {
                MerkleProof proof = MerkleTree.getProof(ids, id);
                assertTrue(MerkleTree.verifyProof(proof, root, id),
                    "proof for " + id + " must verify against root");
            }
        }

        @Test
        @DisplayName("round-trip: proof verifies for every tx in a 4-element block")
        void roundTrip4() {
            List<String> ids = txIds(4);
            String root = MerkleTree.buildRoot(ids);
            for (String id : ids) {
                assertTrue(MerkleTree.verifyProof(MerkleTree.getProof(ids, id), root, id));
            }
        }

        @Test
        @DisplayName("round-trip: proof verifies for every tx in a 5-element (odd) block")
        void roundTrip5Odd() {
            List<String> ids = txIds(5);
            String root = MerkleTree.buildRoot(ids);
            for (String id : ids) {
                assertTrue(MerkleTree.verifyProof(MerkleTree.getProof(ids, id), root, id));
            }
        }

        @Test
        @DisplayName("round-trip: proof verifies for every tx in a 7-element (odd) block")
        void roundTrip7Odd() {
            List<String> ids = txIds(7);
            String root = MerkleTree.buildRoot(ids);
            for (String id : ids) {
                assertTrue(MerkleTree.verifyProof(MerkleTree.getProof(ids, id), root, id));
            }
        }

        @Test
        @DisplayName("single-tx block: proof verifies correctly")
        void singleTxVerifies() {
            String txId = "single-only";
            String root = MerkleTree.buildRoot(List.of(txId));
            MerkleProof proof = MerkleTree.getProof(List.of(txId), txId);
            assertTrue(MerkleTree.verifyProof(proof, root, txId));
        }

        @Test
        @DisplayName("proof for wrong txId does not verify")
        void wrongTxIdFails() {
            List<String> ids = txIds(4);
            String root = MerkleTree.buildRoot(ids);
            MerkleProof proof = MerkleTree.getProof(ids, ids.get(0));

            // Verify with a different txId — must fail
            assertFalse(MerkleTree.verifyProof(proof, root, ids.get(1)),
                "proof for tx[0] must not verify for tx[1]");
        }

        @Test
        @DisplayName("proof does not verify against a wrong root")
        void wrongRootFails() {
            List<String> ids = txIds(4);
            String root = MerkleTree.buildRoot(ids);
            String wrong = HashUtil.sha256("NOT-THE-REAL-ROOT");

            MerkleProof proof = MerkleTree.getProof(ids, ids.get(0));
            assertFalse(MerkleTree.verifyProof(proof, wrong, ids.get(0)),
                "proof must not verify against a tampered root");

            // But still verifies against the correct root
            assertTrue(MerkleTree.verifyProof(proof, root, ids.get(0)));
        }

        @Test
        @DisplayName("null proof throws NullPointerException")
        void nullProofThrows() {
            assertThrows(NullPointerException.class,
                () -> MerkleTree.verifyProof(null, "root", "txId"));
        }

        @Test
        @DisplayName("null expectedRoot throws NullPointerException")
        void nullRootThrows() {
            List<String> ids = txIds(2);
            MerkleProof proof = MerkleTree.getProof(ids, ids.get(0));
            assertThrows(NullPointerException.class,
                () -> MerkleTree.verifyProof(proof, null, ids.get(0)));
        }

        @Test
        @DisplayName("blank expectedRoot throws IllegalArgumentException")
        void blankRootThrows() {
            List<String> ids = txIds(2);
            MerkleProof proof = MerkleTree.getProof(ids, ids.get(0));
            assertThrows(IllegalArgumentException.class,
                () -> MerkleTree.verifyProof(proof, "  ", ids.get(0)));
        }

        @Test
        @DisplayName("null txId throws NullPointerException")
        void nullTxIdThrows() {
            List<String> ids = txIds(2);
            String root = MerkleTree.buildRoot(ids);
            MerkleProof proof = MerkleTree.getProof(ids, ids.get(0));
            assertThrows(NullPointerException.class,
                () -> MerkleTree.verifyProof(proof, root, null));
        }
    }

    // ─── MerkleProof value type ───────────────────────────────────────────────

    @Nested
    @DisplayName("MerkleProof")
    class MerkleProofTests {

        @Test
        @DisplayName("getDepth() matches path size")
        void depthMatchesPathSize() {
            List<String> ids = txIds(8);
            MerkleProof proof = MerkleTree.getProof(ids, ids.get(0));
            assertEquals(proof.getPath().size(), proof.getDepth());
        }

        @Test
        @DisplayName("path list is unmodifiable")
        void pathIsUnmodifiable() {
            List<String> ids = txIds(4);
            MerkleProof proof = MerkleTree.getProof(ids, ids.get(0));
            assertThrows(UnsupportedOperationException.class,
                () -> proof.getPath().clear());
        }

        @Test
        @DisplayName("null transactionId throws NullPointerException")
        void nullTransactionIdThrows() {
            assertThrows(NullPointerException.class,
                () -> new MerkleProof(null, List.of()));
        }

        @Test
        @DisplayName("blank transactionId throws IllegalArgumentException")
        void blankTransactionIdThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> new MerkleProof("  ", List.of()));
        }

        @Test
        @DisplayName("toString contains txId")
        void toStringContainsTxId() {
            List<String> ids = txIds(2);
            MerkleProof proof = MerkleTree.getProof(ids, ids.get(0));
            assertTrue(proof.toString().contains(ids.get(0).substring(0, 5)));
        }
    }
}
