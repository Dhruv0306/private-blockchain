package com.privatechain.access.rbac;

/**
 * Enumeration of node roles in a private blockchain network.
 *
 * <p>Each role carries specific capabilities for block submission, transaction submission,
 * validation, and reading from the chain. This enum is used by {@link PermissionManager}
 * to enforce role-based access control (FR-AC-02, FR-AC-03).</p>
 *
 * <h2>Role hierarchy and capabilities</h2>
 * <ul>
 *   <li><strong>NODE_ADMIN</strong>: Full authority. Can submit blocks, transactions, validate,
 *       read, and manage the network (add/remove peers).</li>
 *   <li><strong>NODE_MINER</strong>: Can produce blocks and submit transactions, but cannot
 *       validate blocks produced by other nodes.</li>
 *   <li><strong>NODE_OBSERVER</strong>: Read-only access. Can query the chain and view events
 *       but cannot submit blocks or transactions.</li>
 * </ul>
 *
 * @see PermissionManager
 * @since 1.0.0
 */
public enum NodeRole {

    /**
     * Administrator node with unrestricted permissions.
     * Can submit blocks, submit transactions, validate blocks, and read all data.
     */
    NODE_ADMIN("admin"),

    /**
     * Mining node with permission to produce and submit blocks, and submit transactions.
     * Cannot validate or review blocks (assumes the network enforces strict consensus rules).
     */
    NODE_MINER("miner"),

    /**
     * Observer node with read-only access to the chain.
     * Cannot submit transactions or blocks but can query state and listen to events.
     */
    NODE_OBSERVER("observer");

    // ─── Fields ───────────────────────────────────────────────────────────────

    private final String roleName;

    // ─── Constructor ──────────────────────────────────────────────────────────

    /**
     * Constructs a {@code NodeRole} with a user-friendly name.
     *
     * @param roleName the human-readable name (e.g. "admin", "miner")
     */
    NodeRole(String roleName) {
        this.roleName = roleName;
    }

    // ─── Accessors ────────────────────────────────────────────────────────────

    /**
     * Returns the user-friendly name of this role.
     *
     * @return role name (lowercase)
     */
    public String roleName() {
        return roleName;
    }

    // ─── Capability checks ────────────────────────────────────────────────────

    /**
     * Returns {@code true} if this role is permitted to submit blocks.
     *
     * @return {@code true} for {@link #NODE_ADMIN} and {@link #NODE_MINER}
     */
    public boolean canSubmitBlock() {
        return this == NODE_ADMIN || this == NODE_MINER;
    }

    /**
     * Returns {@code true} if this role is permitted to submit transactions.
     *
     * @return {@code true} for {@link #NODE_ADMIN} and {@link #NODE_MINER}
     */
    public boolean canSubmitTransaction() {
        return this == NODE_ADMIN || this == NODE_MINER;
    }

    /**
     * Returns {@code true} if this role is permitted to validate blocks.
     *
     * @return {@code true} for {@link #NODE_ADMIN} only
     */
    public boolean canValidateBlock() {
        return this == NODE_ADMIN;
    }

    /**
     * Returns {@code true} if this role is permitted to read chain data.
     *
     * @return {@code true} for all roles
     */
    public boolean canRead() {
        return true;
    }
}

