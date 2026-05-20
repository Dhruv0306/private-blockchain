package com.privatechain.access.allowlist;

import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Manages the allowlist of permitted node identifiers in a private blockchain network.
 *
 * <p>An allowlist is the set of node IDs which are permitted to send blocks, transactions,
 * and other network messages to this node. Messages from non-allowlisted nodes are
 * silently dropped before deserialization (FR-AC-01, NFR-SEC-04).
 *
 * <p>The allowlist is checked early in the message-processing pipeline in {@link
 * com.privatechain.network.rpc.NodeServer} to prevent resource exhaustion attacks
 * and maintain privacy in a permission-restricted network.</p>
 *
 * <h2>Thread safety</h2>
 * This class uses a {@link CopyOnWriteArraySet} for the allowlist, making it safe for
 * concurrent reads. The trade-off is that modifications (add/remove) are slower than reads,
 * but this is acceptable because permission updates happen infrequently.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * AllowlistManager allowlist = new AllowlistManager();
 * allowlist.add("trusted-node-1");
 * allowlist.add("trusted-node-2");
 *
 * if (!allowlist.isAllowed("peer-123")) {
 *     log.warn("Rejecting message from non-allowlisted node: {}", peer);
 *     return;
 * }
 * }</pre>
 *
 * @see InvitationService
 * @since 1.0.0
 */
public class AllowlistManager {

    // ─── Fields ───────────────────────────────────────────────────────────────

    /**
     * Thread-safe set of allowlisted node IDs.
     * Uses {@link CopyOnWriteArraySet} to allow concurrent reads without locking.
     */
    private final Set<String> allowedNodes = new CopyOnWriteArraySet<>();

    // ─── Constructor ──────────────────────────────────────────────────────────

    /**
     * Constructs an empty {@code AllowlistManager}.
     *
     * <p>No nodes are allowed initially. Call {@link #add(String)} or
     * {@link #loadFromStore(AllowlistStore)} to populate the allowlist.</p>
     */
    public AllowlistManager() {
    }

    /**
     * Constructs an {@code AllowlistManager} pre-populated with initial node IDs.
     *
     * @param initialNodes collection of node IDs to allowlist (non-null; may be empty)
     * @throws NullPointerException if initialNodes is null
     */
    public AllowlistManager(Collection<String> initialNodes) {
        Objects.requireNonNull(initialNodes, "initialNodes must not be null");
        // Validate and add all nodes
        for (String nodeId : initialNodes) {
            if (nodeId != null && !nodeId.isBlank()) {
                allowedNodes.add(nodeId);
            }
        }
    }

    // ─── Allowlist queries ────────────────────────────────────────────────────

    /**
     * Checks whether a node ID is on the allowlist.
     *
     * <p>This method returns very quickly because it does not acquire locks;
     * it is safe to call from hot paths such as inbound message handlers.</p>
     *
     * @param nodeId the node identifier to check (non-null, non-blank)
     * @return {@code true} if the node is allowlisted; {@code false} otherwise
     * @throws NullPointerException     if nodeId is null
     * @throws IllegalArgumentException if nodeId is blank
     */
    public boolean isAllowed(String nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        if (nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId must not be blank");
        }
        return allowedNodes.contains(nodeId);
    }

    /**
     * Returns the number of allowlisted nodes.
     *
     * @return count of nodes on the allowlist
     */
    public int size() {
        return allowedNodes.size();
    }

    /**
     * Returns {@code true} if the allowlist is empty.
     *
     * @return {@code true} if no nodes are allowlisted
     */
    public boolean isEmpty() {
        return allowedNodes.isEmpty();
    }

    /**
     * Returns all currently allowlisted node IDs.
     *
     * <p>The returned set is a snapshot; modifications to the set do not affect
     * the manager's internal state.</p>
     *
     * @return unmodifiable set of allowlisted node IDs
     */
    public Set<String> getAllowedNodes() {
        return Collections.unmodifiableSet(new HashSet<>(allowedNodes));
    }

    // ─── Allowlist modification ───────────────────────────────────────────────

    /**
     * Adds a node ID to the allowlist.
     *
     * <p>If the node ID is already on the list, this is a no-op.</p>
     *
     * @param nodeId the node identifier to add (non-null, non-blank)
     * @throws NullPointerException     if nodeId is null
     * @throws IllegalArgumentException if nodeId is blank
     */
    public void add(String nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        if (nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId must not be blank");
        }
        allowedNodes.add(nodeId);
    }

    /**
     * Removes a node ID from the allowlist.
     *
     * <p>If the node ID is not on the list, this is a no-op.</p>
     *
     * @param nodeId the node identifier to remove (non-null, non-blank)
     * @throws NullPointerException     if nodeId is null
     * @throws IllegalArgumentException if nodeId is blank
     */
    public void remove(String nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        if (nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId must not be blank");
        }
        allowedNodes.remove(nodeId);
    }

    /**
     * Replaces the allowlist with a new set of node IDs.
     *
     * <p>All previously allowlisted nodes are removed, and the provided collection
     * becomes the new allowlist.</p>
     *
     * @param newAllowlist collection of node IDs (non-null; may be empty)
     * @throws NullPointerException if newAllowlist is null
     */
    public void replaceAllowlist(Collection<String> newAllowlist) {
        Objects.requireNonNull(newAllowlist, "newAllowlist must not be null");
        allowedNodes.clear();
        for (String nodeId : newAllowlist) {
            if (nodeId != null && !nodeId.isBlank()) {
                allowedNodes.add(nodeId);
            }
        }
    }

    /**
     * Clears the allowlist, removing all node IDs.
     */
    public void clear() {
        allowedNodes.clear();
    }

    // ─── Invitation verification ──────────────────────────────────────────────

    /**
     * Verifies an invitation token and adds the requested node to the allowlist.
     *
     * <p>This method is called by the {@link InvitationService} after validating
     * a token's signature and expiry. If both checks pass, the node ID is added
     * to the allowlist.</p>
     *
     * @param nodeId the node ID to add based on a valid invitation (non-null, non-blank)
     * @throws NullPointerException     if nodeId is null
     * @throws IllegalArgumentException if nodeId is blank
     */
    public void addFromInvitation(String nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        if (nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId must not be blank");
        }
        add(nodeId);
    }
}

