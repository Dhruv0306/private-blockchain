package com.privatechain.access;

import com.privatechain.access.allowlist.AllowlistManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test for {@link AllowlistManager}.
 *
 * <p>Verifies allowlist queries, modifications, and state consistency.</p>
 */
class AllowlistManagerTest {

    private AllowlistManager allowlistManager;

    @BeforeEach
    void setUp() {
        allowlistManager = new AllowlistManager();
    }

    // ─── Constructor tests ─────────────────────────────────────────────────────

    @Test
    void defaultConstructorCreatesEmptyAllowlist() {
        assertTrue(allowlistManager.isEmpty());
        assertEquals(0, allowlistManager.size());
    }

    @Test
    void constructorWithInitialNodesPopulatesAllowlist() {
        Set<String> initial = Set.of("node-1", "node-2", "node-3");
        allowlistManager = new AllowlistManager(initial);
        assertEquals(3, allowlistManager.size());
        assertTrue(allowlistManager.isAllowed("node-1"));
        assertTrue(allowlistManager.isAllowed("node-2"));
        assertTrue(allowlistManager.isAllowed("node-3"));
    }

    @Test
    void constructorWithInitialNodesFiltersBlankNodeIds() {
        List<String> initial = Arrays.asList("node-1", "", "node-2", "   ", null, "node-3");
        allowlistManager = new AllowlistManager(initial);
        assertEquals(3, allowlistManager.size());
        assertTrue(allowlistManager.isAllowed("node-1"));
        assertTrue(allowlistManager.isAllowed("node-2"));
        assertTrue(allowlistManager.isAllowed("node-3"));
    }

    @Test
    void constructorRequiresNonNullInitialNodes() {
        assertThrows(NullPointerException.class, () -> new AllowlistManager(null));
    }

    // ─── Allowlist query tests ────────────────────────────────────────────────

    @Test
    void isAllowedReturnsFalseForUnknownNode() {
        assertFalse(allowlistManager.isAllowed("unknown-node"));
    }

    @Test
    void isAllowedReturnsTrueForAddedNode() {
        allowlistManager.add("node-1");
        assertTrue(allowlistManager.isAllowed("node-1"));
    }

    @Test
    void isAllowedRequiresNonNullNodeId() {
        assertThrows(NullPointerException.class, () -> allowlistManager.isAllowed(null));
    }

    @Test
    void isAllowedRequiresNonBlankNodeId() {
        assertThrows(IllegalArgumentException.class, () -> allowlistManager.isAllowed(""));
        assertThrows(IllegalArgumentException.class, () -> allowlistManager.isAllowed("   "));
    }

    @Test
    void sizeReturnsCorrectCount() {
        assertEquals(0, allowlistManager.size());
        allowlistManager.add("node-1");
        assertEquals(1, allowlistManager.size());
        allowlistManager.add("node-2");
        assertEquals(2, allowlistManager.size());
    }

    @Test
    void isEmptyReturnsTrueWhenEmpty() {
        assertTrue(allowlistManager.isEmpty());
    }

    @Test
    void isEmptyReturnsFalseWhenNotEmpty() {
        allowlistManager.add("node-1");
        assertFalse(allowlistManager.isEmpty());
    }

    // ─── Allowlist addition tests ──────────────────────────────────────────────

    @Test
    void addNodeMakesItAllowed() {
        allowlistManager.add("node-1");
        assertTrue(allowlistManager.isAllowed("node-1"));
    }

    @Test
    void addDuplicateNodeIsNoOp() {
        allowlistManager.add("node-1");
        allowlistManager.add("node-1");
        assertEquals(1, allowlistManager.size());
    }

    @Test
    void addRequiresNonNullNodeId() {
        assertThrows(NullPointerException.class, () -> allowlistManager.add(null));
    }

    @Test
    void addRequiresNonBlankNodeId() {
        assertThrows(IllegalArgumentException.class, () -> allowlistManager.add(""));
        assertThrows(IllegalArgumentException.class, () -> allowlistManager.add("   "));
    }

    // ─── Allowlist removal tests ───────────────────────────────────────────────

    @Test
    void removeNodeMakesItUnallowed() {
        allowlistManager.add("node-1");
        assertTrue(allowlistManager.isAllowed("node-1"));
        allowlistManager.remove("node-1");
        assertFalse(allowlistManager.isAllowed("node-1"));
    }

    @Test
    void removeUnknownNodeIsNoOp() {
        allowlistManager.remove("unknown-node");
        assertEquals(0, allowlistManager.size());
    }

    @Test
    void removeRequiresNonNullNodeId() {
        assertThrows(NullPointerException.class, () -> allowlistManager.remove(null));
    }

    @Test
    void removeRequiresNonBlankNodeId() {
        assertThrows(IllegalArgumentException.class, () -> allowlistManager.remove(""));
        assertThrows(IllegalArgumentException.class, () -> allowlistManager.remove("   "));
    }

    // ─── Allowlist listing tests ───────────────────────────────────────────────

    @Test
    void getAllowedNodesReturnsEmptySetWhenEmpty() {
        Set<String> nodes = allowlistManager.getAllowedNodes();
        assertTrue(nodes.isEmpty());
    }

    @Test
    void getAllowedNodesReturnsAllAddedNodes() {
        allowlistManager.add("node-1");
        allowlistManager.add("node-2");
        allowlistManager.add("node-3");

        Set<String> nodes = allowlistManager.getAllowedNodes();
        assertEquals(3, nodes.size());
        assertTrue(nodes.contains("node-1"));
        assertTrue(nodes.contains("node-2"));
        assertTrue(nodes.contains("node-3"));
    }

    @Test
    void getAllowedNodesReturnsUnmodifiableSet() {
        allowlistManager.add("node-1");
        Set<String> nodes = allowlistManager.getAllowedNodes();
        assertThrows(UnsupportedOperationException.class, () -> nodes.add("node-2"));
    }

    // ─── Allowlist replacement tests ───────────────────────────────────────────

    @Test
    void replaceAllowlistClearsAndRepopulates() {
        allowlistManager.add("node-1");
        allowlistManager.add("node-2");
        assertEquals(2, allowlistManager.size());

        Set<String> newAllowlist = Set.of("node-3", "node-4");
        allowlistManager.replaceAllowlist(newAllowlist);

        assertEquals(2, allowlistManager.size());
        assertFalse(allowlistManager.isAllowed("node-1"));
        assertFalse(allowlistManager.isAllowed("node-2"));
        assertTrue(allowlistManager.isAllowed("node-3"));
        assertTrue(allowlistManager.isAllowed("node-4"));
    }

    @Test
    void replaceAllowlistWithEmptyListClearsAllowlist() {
        allowlistManager.add("node-1");
        allowlistManager.add("node-2");
        allowlistManager.replaceAllowlist(Collections.emptySet());
        assertTrue(allowlistManager.isEmpty());
    }

    @Test
    void replaceAllowlistFiltersBlankNodeIds() {
        allowlistManager.replaceAllowlist(Arrays.asList("node-1", "", "node-2", "   ", null));
        assertEquals(2, allowlistManager.size());
        assertTrue(allowlistManager.isAllowed("node-1"));
        assertTrue(allowlistManager.isAllowed("node-2"));
    }

    @Test
    void replaceAllowlistRequiresNonNullCollection() {
        assertThrows(NullPointerException.class, () -> allowlistManager.replaceAllowlist(null));
    }

    // ─── Allowlist clear tests ────────────────────────────────────────────────

    @Test
    void clearRemovesAllNodes() {
        allowlistManager.add("node-1");
        allowlistManager.add("node-2");
        assertEquals(2, allowlistManager.size());

        allowlistManager.clear();

        assertTrue(allowlistManager.isEmpty());
        assertFalse(allowlistManager.isAllowed("node-1"));
        assertFalse(allowlistManager.isAllowed("node-2"));
    }

    @Test
    void clearOnEmptyAllowlistIsNoOp() {
        allowlistManager.clear();
        assertTrue(allowlistManager.isEmpty());
    }

    // ─── Invitation integration tests ──────────────────────────────────────────

    @Test
    void addFromInvitationAddsNodeToAllowlist() {
        allowlistManager.addFromInvitation("invited-node");
        assertTrue(allowlistManager.isAllowed("invited-node"));
    }

    @Test
    void addFromInvitationRequiresNonBlankNodeId() {
        assertThrows(IllegalArgumentException.class, () -> allowlistManager.addFromInvitation(""));
    }

    // ─── Thread safety tests ───────────────────────────────────────────────────

    @Test
    void multipleThreadsCanConcurrentlyCheckAllowance() throws InterruptedException {
        allowlistManager.add("node-1");

        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                assertTrue(allowlistManager.isAllowed("node-1"));
            }
        });

        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                assertTrue(allowlistManager.isAllowed("node-1"));
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();
    }

    @Test
    void multipleThreadsCanConcurrentlyModifyAllowlist() throws InterruptedException {
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                allowlistManager.add("node-" + i);
            }
        });

        Thread t2 = new Thread(() -> {
            for (int i = 100; i < 200; i++) {
                allowlistManager.add("node-" + i);
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        assertEquals(200, allowlistManager.size());
    }
}

