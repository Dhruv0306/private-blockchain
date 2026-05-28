package com.privatechain.network.peer;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Thread-safe in-memory store for known {@link Peer} instances.
 *
 * <p>{@code PeerStore} acts as the single source of truth for the peer list maintained
 * by {@link PeerManager}. It provides atomic put/get/remove operations and supports
 * snapshot queries. In a production deployment this class would back its map with a
 * persistent store (e.g., a small embedded JSON file or LevelDB entry) so that the
 * node can reconnect to previously known peers after a restart without relying solely
 * on seed peers.</p>
 *
 * <h2>Thread safety</h2>
 * <p>All methods are safe for concurrent use. Reads and writes are serialized via
 * {@link ConcurrentHashMap} which provides lock-free reads and striped-lock writes.</p>
 *
 * <h2>Key design decision</h2>
 * <p>Peers are keyed by {@code nodeId} (not by {@code host:port}) because a peer's
 * IP address can change on reconnect while its cryptographic identity stays constant.
 * This ensures that a reconnected peer replaces its stale entry rather than creating
 * a duplicate.</p>
 *
 * @see PeerManager
 * @since 1.0.0
 */
public final class PeerStore {

    private static final Logger LOGGER = Logger.getLogger(PeerStore.class.getName());

    // ─── Fields ───────────────────────────────────────────────────────────────

    /**
     * Primary peer index keyed by {@link Peer#getNodeId()}.
     * {@link ConcurrentHashMap} is used for lock-free reads in the hot path.
     */
    private final ConcurrentHashMap<String, Peer> peers = new ConcurrentHashMap<>();

    // ─── Writes ───────────────────────────────────────────────────────────────

    /**
     * Inserts or replaces a peer entry keyed by {@link Peer#getNodeId()}.
     *
     * <p>If a peer with the same node ID already exists (e.g., reconnecting on a
     * new IP address), the old entry is overwritten atomically.</p>
     *
     * @param peer the peer to store (non-null)
     * @throws NullPointerException if peer is null
     */
    public void put(Peer peer) {
        Objects.requireNonNull(peer, "peer must not be null");
        peers.put(peer.getNodeId(), peer);
        LOGGER.fine(() -> "PeerStore: added/updated peer " + peer.getNodeId()
            + " at " + peer.getAddress());
    }

    /**
     * Removes a peer from the store by node ID.
     *
     * <p>No-op if the node ID is not present.</p>
     *
     * @param nodeId the node ID to remove (non-null, non-blank)
     * @throws NullPointerException     if nodeId is null
     * @throws IllegalArgumentException if nodeId is blank
     */
    public void remove(String nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        if (nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId must not be blank");
        }
        Peer removed = peers.remove(nodeId);
        if (removed != null) {
            LOGGER.fine(() -> "PeerStore: removed peer " + nodeId);
        }
    }

    /**
     * Removes all peers from the store.
     */
    public void clear() {
        int count = peers.size();
        peers.clear();
        LOGGER.fine(() -> "PeerStore: cleared " + count + " peers");
    }

    // ─── Reads ────────────────────────────────────────────────────────────────

    /**
     * Returns the peer with the given node ID, or {@link Optional#empty()} if not found.
     *
     * @param nodeId the node ID to look up (non-null, non-blank)
     * @return optional peer
     * @throws NullPointerException     if nodeId is null
     * @throws IllegalArgumentException if nodeId is blank
     */
    public Optional<Peer> get(String nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        if (nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId must not be blank");
        }
        return Optional.ofNullable(peers.get(nodeId));
    }

    /**
     * Returns {@code true} if the store contains a peer with the given node ID.
     *
     * @param nodeId the node ID to check (non-null, non-blank)
     * @return {@code true} if present
     */
    public boolean contains(String nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        return !nodeId.isBlank() && peers.containsKey(nodeId);
    }

    /**
     * Returns a point-in-time snapshot of all known peers.
     *
     * <p>Modifications to the returned list do not affect the store. Peers are
     * returned in an unspecified order; callers that need a deterministic order
     * should sort the list themselves.</p>
     *
     * @return unmodifiable list of all current peers
     */
    public List<Peer> getAll() {
        return List.copyOf(peers.values());
    }

    /**
     * Returns a snapshot of all peers whose node ID is in the given collection.
     *
     * @param nodeIds the set of node IDs to include (non-null)
     * @return unmodifiable list of matched peers
     */
    public List<Peer> getAll(Collection<String> nodeIds) {
        Objects.requireNonNull(nodeIds, "nodeIds must not be null");
        return peers.values().stream()
            .filter(p -> nodeIds.contains(p.getNodeId()))
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Returns the total number of peers currently stored.
     *
     * @return peer count (&ge; 0)
     */
    public int size() {
        return peers.size();
    }

    /**
     * Returns {@code true} if the store contains no peers.
     *
     * @return {@code true} if empty
     */
    public boolean isEmpty() {
        return peers.isEmpty();
    }

    /**
     * Returns a human-readable summary of the peer store (for logging).
     *
     * @return string representation
     */
    @Override
    public String toString() {
        return "PeerStore{size=" + peers.size() + '}';
    }
}
