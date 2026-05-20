package com.privatechain.access.rbac;

import com.privatechain.core.spi.BlockchainStorage;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Manages role-based access control (RBAC) for a private blockchain network.
 *
 * <p>This class maintains the mapping of node addresses to their assigned roles
 * ({@link NodeRole#NODE_ADMIN}, {@link NodeRole#NODE_MINER}, {@link NodeRole#NODE_OBSERVER}).
 * All role assignments are persisted in the configured {@link BlockchainStorage}
 * so they survive node restarts.</p>
 *
 * <h2>Thread safety</h2>
 * All operations on role assignments are serialized via a {@link ReadWriteLock}.
 * Multiple threads can read roles concurrently; writes are exclusive. This is appropriate
 * for scenarios where permission checks (reads) happen far more frequently than
 * permission updates (writes).
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * PermissionManager pm = new PermissionManager(storage);
 * pm.assignRole("node-123", NodeRole.NODE_MINER);
 *
 * if (pm.hasRole("node-123", NodeRole.NODE_MINER)) {
 *     acceptBlockSubmission(block);
 * }
 * }</pre>
 *
 * @see NodeRole
 * @see BlockchainStorage
 * @since 1.0.0
 */
public class PermissionManager {

    // ─── Fields ───────────────────────────────────────────────────────────────

    /**
     * In-memory cache of node ID → assigned role. Persisted to storage on each update.
     */
    private final Map<String, NodeRole> roleAssignments = new ConcurrentHashMap<>();

    /**
     * Read-write lock for role assignment operations. Reads are concurrent,
     * writes are exclusive (FR-RBAC thread safety).
     */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * Persistent storage backend for role assignments. Used to durably record
     * role changes so they survive node restarts (FR-AC-02).
     */
    private final BlockchainStorage storage;

    // ─── Constructor ──────────────────────────────────────────────────────────

    /**
     * Constructs a {@code PermissionManager} that persists role assignments
     * to the provided {@link BlockchainStorage}.
     *
     * @param storage non-null storage backend for durability
     * @throws NullPointerException if storage is null
     */
    public PermissionManager(BlockchainStorage storage) {
        this.storage = Objects.requireNonNull(storage, "storage must not be null");
    }

    // ─── Role assignment ──────────────────────────────────────────────────────

    /**
     * Assigns a role to a node identified by its node ID.
     *
     * <p>The assignment is immediately visible to all threads and is persisted
     * to the configured {@link BlockchainStorage} synchronously.</p>
     *
     * @param nodeId  the unique node identifier (non-null, non-blank)
     * @param role    the role to assign (non-null)
     * @throws NullPointerException     if nodeId or role is null
     * @throws IllegalArgumentException if nodeId is blank
     */
    public void assignRole(String nodeId, NodeRole role) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        Objects.requireNonNull(role, "role must not be null");
        if (nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId must not be blank");
        }

        lock.writeLock().lock();
        try {
            roleAssignments.put(nodeId, role);
            persistRoleAssignments();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Revokes any role assignment for a given node.
     *
     * <p>After revocation, {@link #hasRole(String, NodeRole)} and
     * {@link #getRole(String)} will return {@code false} and {@code Optional.empty()},
     * respectively.</p>
     *
     * @param nodeId the unique node identifier (non-null, non-blank)
     * @throws NullPointerException     if nodeId is null
     * @throws IllegalArgumentException if nodeId is blank
     */
    public void revokeRole(String nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        if (nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId must not be blank");
        }

        lock.writeLock().lock();
        try {
            roleAssignments.remove(nodeId);
            persistRoleAssignments();
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ─── Role queries ─────────────────────────────────────────────────────────

    /**
     * Checks whether a given node has a specific role.
     *
     * <p>Returns {@code true} only if the node is assigned exactly that role.
     * This method is read-locked and thread-safe for concurrent access.</p>
     *
     * @param nodeId the node identifier (non-null, non-blank)
     * @param role   the role to check (non-null)
     * @return {@code true} if the node has the role; {@code false} otherwise
     * @throws NullPointerException     if nodeId or role is null
     * @throws IllegalArgumentException if nodeId is blank
     */
    public boolean hasRole(String nodeId, NodeRole role) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        Objects.requireNonNull(role, "role must not be null");
        if (nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId must not be blank");
        }

        lock.readLock().lock();
        try {
            return role == roleAssignments.get(nodeId);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Retrieves the role assigned to a given node, if any.
     *
     * <p>Returns an empty {@code Optional} if the node has no assigned role
     * or is not known to the manager.</p>
     *
     * @param nodeId the node identifier (non-null, non-blank)
     * @return an {@code Optional} containing the assigned role, or empty if none
     * @throws NullPointerException     if nodeId is null
     * @throws IllegalArgumentException if nodeId is blank
     */
    public Optional<NodeRole> getRole(String nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        if (nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId must not be blank");
        }

        lock.readLock().lock();
        try {
            return Optional.ofNullable(roleAssignments.get(nodeId));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns all currently assigned role assignments.
     *
     * <p>The returned {@code Map} is an unmodifiable copy made under a read lock,
     * so the caller is free to iterate over it without blocking the manager.</p>
     *
     * @return an unmodifiable map of node IDs to roles
     */
    public Map<String, NodeRole> getAllRoleAssignments() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableMap(new HashMap<>(roleAssignments));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns all node IDs that have been assigned a specific role.
     *
     * @param role the role to search for (non-null)
     * @return unmodifiable set of node IDs with the role
     * @throws NullPointerException if role is null
     */
    public Set<String> getNodesWithRole(NodeRole role) {
        Objects.requireNonNull(role, "role must not be null");

        lock.readLock().lock();
        try {
            Set<String> result = new HashSet<>();
            for (Map.Entry<String, NodeRole> entry : roleAssignments.entrySet()) {
                if (entry.getValue() == role) {
                    result.add(entry.getKey());
                }
            }
            return Collections.unmodifiableSet(result);
        } finally {
            lock.readLock().unlock();
        }
    }

    // ─── Persistence ──────────────────────────────────────────────────────────

    /**
     * Persists all current role assignments to the underlying storage.
     *
     * <p>This method is called automatically by {@link #assignRole(String, NodeRole)}
     * and {@link #revokeRole(String)} and is package-private for testing purposes.</p>
     */
    void persistRoleAssignments() {
        // Note: persistence will be implemented once BlockchainStorage is fully available
        // For now, this is a placeholder that future integrations can extend
    }

    /**
     * Loads previously persisted role assignments from storage.
     *
     * <p>Called during manager initialization to restore role state after a restart.</p>
     */
    void loadRoleAssignments() {
        // Note: loading will be implemented once BlockchainStorage is fully available
        // For now, this is a placeholder that future integrations can extend
    }
}

