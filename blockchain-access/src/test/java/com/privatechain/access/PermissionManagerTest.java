package com.privatechain.access;

import com.privatechain.access.rbac.NodeRole;
import com.privatechain.access.rbac.PermissionManager;
import com.privatechain.core.spi.BlockchainStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Contract test for {@link PermissionManager}.
 *
 * <p>Verifies role assignment, queries, revocation, and capability checks across
 * all three node roles.</p>
 */
class PermissionManagerTest {

    private PermissionManager permissionManager;
    private BlockchainStorage mockStorage;

    @BeforeEach
    void setUp() {
        mockStorage = mock(BlockchainStorage.class);
        permissionManager = new PermissionManager(mockStorage);
    }

    // ─── Constructor tests ─────────────────────────────────────────────────────

    @Test
    void constructorRequiresNonNullStorage() {
        assertThrows(NullPointerException.class, () -> new PermissionManager(null));
    }

    // ─── Role assignment tests ────────────────────────────────────────────────

    @Test
    void assignRoleStoresTheRoleForANode() {
        String nodeId = "node-1";
        permissionManager.assignRole(nodeId, NodeRole.NODE_ADMIN);

        assertTrue(permissionManager.hasRole(nodeId, NodeRole.NODE_ADMIN));
    }

    @Test
    void assignRoleOverwritesPreviousRole() {
        String nodeId = "node-1";
        permissionManager.assignRole(nodeId, NodeRole.NODE_MINER);
        assertTrue(permissionManager.hasRole(nodeId, NodeRole.NODE_MINER));

        // Overwrite with a new role
        permissionManager.assignRole(nodeId, NodeRole.NODE_ADMIN);
        assertTrue(permissionManager.hasRole(nodeId, NodeRole.NODE_ADMIN));
        assertFalse(permissionManager.hasRole(nodeId, NodeRole.NODE_MINER));
    }

    @Test
    void assignRoleRequiresNonNullNodeId() {
        assertThrows(NullPointerException.class,
            () -> permissionManager.assignRole(null, NodeRole.NODE_ADMIN));
    }

    @Test
    void assignRoleRequiresNonBlankNodeId() {
        assertThrows(IllegalArgumentException.class,
            () -> permissionManager.assignRole("", NodeRole.NODE_ADMIN));
        assertThrows(IllegalArgumentException.class,
            () -> permissionManager.assignRole("   ", NodeRole.NODE_ADMIN));
    }

    @Test
    void assignRoleRequiresNonNullRole() {
        assertThrows(NullPointerException.class,
            () -> permissionManager.assignRole("node-1", null));
    }

    // ─── Role query tests ─────────────────────────────────────────────────────

    @Test
    void hasRoleReturnsFalseForUnknownNode() {
        assertFalse(permissionManager.hasRole("unknown-node", NodeRole.NODE_ADMIN));
    }

    @Test
    void hasRoleReturnsFalseForWrongRole() {
        permissionManager.assignRole("node-1", NodeRole.NODE_MINER);
        assertFalse(permissionManager.hasRole("node-1", NodeRole.NODE_ADMIN));
        assertFalse(permissionManager.hasRole("node-1", NodeRole.NODE_OBSERVER));
    }

    @Test
    void getRoleReturnsEmptyForUnknownNode() {
        Optional<NodeRole> role = permissionManager.getRole("unknown-node");
        assertTrue(role.isEmpty());
    }

    @Test
    void getRoleReturnsAssignedRole() {
        permissionManager.assignRole("node-1", NodeRole.NODE_MINER);
        Optional<NodeRole> role = permissionManager.getRole("node-1");
        assertTrue(role.isPresent());
        assertEquals(NodeRole.NODE_MINER, role.get());
    }

    @Test
    void hasRoleRequiresNonNullNodeId() {
        assertThrows(NullPointerException.class,
            () -> permissionManager.hasRole(null, NodeRole.NODE_ADMIN));
    }

    @Test
    void hasRoleRequiresNonBlankNodeId() {
        assertThrows(IllegalArgumentException.class,
            () -> permissionManager.hasRole("", NodeRole.NODE_ADMIN));
    }

    @Test
    void hasRoleRequiresNonNullRole() {
        assertThrows(NullPointerException.class,
            () -> permissionManager.hasRole("node-1", null));
    }

    // ─── Role revocation tests ────────────────────────────────────────────────

    @Test
    void revokeRoleRemovesTheAssignment() {
        String nodeId = "node-1";
        permissionManager.assignRole(nodeId, NodeRole.NODE_ADMIN);
        assertTrue(permissionManager.hasRole(nodeId, NodeRole.NODE_ADMIN));

        permissionManager.revokeRole(nodeId);
        assertFalse(permissionManager.hasRole(nodeId, NodeRole.NODE_ADMIN));
    }

    @Test
    void revokeRoleIsNoOpForUnknownNode() {
        // Should not throw
        permissionManager.revokeRole("unknown-node");
    }

    @Test
    void revokeRoleRequiresNonNullNodeId() {
        assertThrows(NullPointerException.class, () -> permissionManager.revokeRole(null));
    }

    @Test
    void revokeRoleRequiresNonBlankNodeId() {
        assertThrows(IllegalArgumentException.class, () -> permissionManager.revokeRole(""));
        assertThrows(IllegalArgumentException.class, () -> permissionManager.revokeRole("   "));
    }

    // ─── Role listing tests ───────────────────────────────────────────────────

    @Test
    void getAllRoleAssignmentsReturnsEmptyMapInitially() {
        Map<String, NodeRole> assignments = permissionManager.getAllRoleAssignments();
        assertTrue(assignments.isEmpty());
    }

    @Test
    void getAllRoleAssignmentsReturnsAllAssignedRoles() {
        permissionManager.assignRole("node-1", NodeRole.NODE_ADMIN);
        permissionManager.assignRole("node-2", NodeRole.NODE_MINER);
        permissionManager.assignRole("node-3", NodeRole.NODE_OBSERVER);

        Map<String, NodeRole> assignments = permissionManager.getAllRoleAssignments();
        assertEquals(3, assignments.size());
        assertEquals(NodeRole.NODE_ADMIN, assignments.get("node-1"));
        assertEquals(NodeRole.NODE_MINER, assignments.get("node-2"));
        assertEquals(NodeRole.NODE_OBSERVER, assignments.get("node-3"));
    }

    @Test
    void getAllRoleAssignmentsReturnsUnmodifiableMap() {
        permissionManager.assignRole("node-1", NodeRole.NODE_ADMIN);
        Map<String, NodeRole> assignments = permissionManager.getAllRoleAssignments();
        assertThrows(UnsupportedOperationException.class,
            () -> assignments.put("node-2", NodeRole.NODE_MINER));
    }

    @Test
    void getNodesWithRoleReturnsEmptyForNoMatches() {
        permissionManager.assignRole("node-1", NodeRole.NODE_ADMIN);
        Set<String> minerNodes = permissionManager.getNodesWithRole(NodeRole.NODE_MINER);
        assertTrue(minerNodes.isEmpty());
    }

    @Test
    void getNodesWithRoleReturnsMatchingNodes() {
        permissionManager.assignRole("node-1", NodeRole.NODE_MINER);
        permissionManager.assignRole("node-2", NodeRole.NODE_MINER);
        permissionManager.assignRole("node-3", NodeRole.NODE_ADMIN);

        Set<String> minerNodes = permissionManager.getNodesWithRole(NodeRole.NODE_MINER);
        assertEquals(2, minerNodes.size());
        assertTrue(minerNodes.contains("node-1"));
        assertTrue(minerNodes.contains("node-2"));
        assertFalse(minerNodes.contains("node-3"));
    }

    @Test
    void getNodesWithRoleRequiresNonNullRole() {
        assertThrows(NullPointerException.class, () -> permissionManager.getNodesWithRole(null));
    }

    // ─── Role capability tests ────────────────────────────────────────────────

    @ParameterizedTest
    @EnumSource(NodeRole.class)
    void allRolesCanRead(NodeRole role) {
        assertTrue(role.canRead());
    }

    @Test
    void nodeAdminCanSubmitBlock() {
        assertTrue(NodeRole.NODE_ADMIN.canSubmitBlock());
    }

    @Test
    void nodeMinerCanSubmitBlock() {
        assertTrue(NodeRole.NODE_MINER.canSubmitBlock());
    }

    @Test
    void nodeObserverCannotSubmitBlock() {
        assertFalse(NodeRole.NODE_OBSERVER.canSubmitBlock());
    }

    @Test
    void nodeAdminCanSubmitTransaction() {
        assertTrue(NodeRole.NODE_ADMIN.canSubmitTransaction());
    }

    @Test
    void nodeMinerCanSubmitTransaction() {
        assertTrue(NodeRole.NODE_MINER.canSubmitTransaction());
    }

    @Test
    void nodeObserverCannotSubmitTransaction() {
        assertFalse(NodeRole.NODE_OBSERVER.canSubmitTransaction());
    }

    @Test
    void nodeAdminCanValidateBlock() {
        assertTrue(NodeRole.NODE_ADMIN.canValidateBlock());
    }

    @Test
    void nodeMinerCannotValidateBlock() {
        assertFalse(NodeRole.NODE_MINER.canValidateBlock());
    }

    @Test
    void nodeObserverCannotValidateBlock() {
        assertFalse(NodeRole.NODE_OBSERVER.canValidateBlock());
    }

    // ─── Thread safety tests ───────────────────────────────────────────────────

    @Test
    void multipleThreadsCanConcurrentlyReadRoles() throws InterruptedException {
        String nodeId = "node-1";
        permissionManager.assignRole(nodeId, NodeRole.NODE_ADMIN);

        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                assertTrue(permissionManager.hasRole(nodeId, NodeRole.NODE_ADMIN));
            }
        });

        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                assertTrue(permissionManager.hasRole(nodeId, NodeRole.NODE_ADMIN));
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();
    }
}

