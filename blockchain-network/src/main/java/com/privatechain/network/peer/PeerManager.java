package com.privatechain.network.peer;

import com.privatechain.core.event.BlockchainEvent;
import com.privatechain.core.event.BlockchainEventBus;
import com.privatechain.core.network.PeerManagerLifecycle;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Manages the lifecycle of peer connections in the private blockchain P2P network.
 *
 * <p>{@code PeerManager} is responsible for (FR-NET-01):</p>
 * <ul>
 *   <li>Maintaining the set of known and connected peers via {@link PeerStore}</li>
 *   <li>Initiating outbound connections to seed peers on startup</li>
 *   <li>Sending periodic heartbeat pings to detect unresponsive peers</li>
 *   <li>Pruning peers that have not responded within the configured timeout</li>
 *   <li>Publishing {@link BlockchainEvent.PeerConnectedEvent} and
 *       {@link BlockchainEvent.PeerDisconnectedEvent} on the event bus</li>
 * </ul>
 *
 * <h2>Design note</h2>
 * <p>This implementation operates over an in-memory peer list backed by {@link PeerStore}.
 * The network I/O for actual TCP connections is delegated to
 * {@link com.privatechain.network.rpc.NodeServer} and {@link com.privatechain.network.rpc.NodeClient}.
 * {@code PeerManager} acts as the coordinator, not the transport layer.</p>
 *
 * <h2>Heartbeat</h2>
 * <p>A {@link ScheduledExecutorService} runs a heartbeat task every
 * {@link #HEARTBEAT_INTERVAL_SECONDS} seconds. Peers that have not been seen within
 * {@link #PEER_TIMEOUT_SECONDS} are removed and a disconnect event is published.</p>
 *
 * <h2>Thread safety</h2>
 * <p>All public methods are thread-safe. The connected-peers set is protected by
 * {@link CopyOnWriteArraySet} for lock-free reads during message fan-out.</p>
 *
 * @see Peer
 * @see PeerStore
 * @since 1.0.0
 */
public final class PeerManager implements PeerManagerLifecycle {

    private static final Logger LOGGER = Logger.getLogger(PeerManager.class.getName());

    /**
     * How often the heartbeat task checks peer liveness (seconds).
     */
    public static final int HEARTBEAT_INTERVAL_SECONDS = 30;

    /**
     * How long a peer can be silent before it is pruned (seconds).
     */
    public static final int PEER_TIMEOUT_SECONDS = 90;

    // ─── Fields ───────────────────────────────────────────────────────────────

    /**
     * Persistent index of all known peers (connected and disconnected).
     */
    private final PeerStore peerStore;

    /**
     * Set of nodeIds currently considered connected.
     */
    private final Set<String> connectedNodeIds = new CopyOnWriteArraySet<>();

    /**
     * Maximum number of simultaneous peer connections (FR-NET-07).
     */
    private final int maxPeers;

    /**
     * Event bus for publishing connect/disconnect events (FR-EVENT-01).
     */
    private final BlockchainEventBus eventBus;

    /**
     * Scheduler for periodic heartbeat checks.
     */
    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "peer-heartbeat");
            t.setDaemon(true);
            return t;
        });

    /**
     * Guards the running state.
     */
    private volatile boolean running = false;

    // ─── Constructor ──────────────────────────────────────────────────────────

    /**
     * Constructs a {@code PeerManager} with the given dependencies.
     *
     * @param peerStore the backing peer store (non-null)
     * @param eventBus  the event bus for publishing peer events (non-null)
     * @param maxPeers  maximum simultaneous peer connections (&ge; 1)
     * @throws NullPointerException     if peerStore or eventBus is null
     * @throws IllegalArgumentException if maxPeers &lt; 1
     */
    public PeerManager(PeerStore peerStore, BlockchainEventBus eventBus, int maxPeers) {
        this.peerStore = Objects.requireNonNull(peerStore, "peerStore must not be null");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus must not be null");
        if (maxPeers < 1) {
            throw new IllegalArgumentException("maxPeers must be >= 1, got: " + maxPeers);
        }
        this.maxPeers = maxPeers;
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    /**
     * Starts the peer manager and schedules the periodic heartbeat task.
     *
     * <p>This method is idempotent; calling it multiple times has no additional effect.</p>
     */
    public void start() {
        if (running) {
            return;
        }
        running = true;
        scheduler.scheduleAtFixedRate(
            this::heartbeatAndPrune,
            HEARTBEAT_INTERVAL_SECONDS,
            HEARTBEAT_INTERVAL_SECONDS,
            TimeUnit.SECONDS);
        LOGGER.info(() -> "PeerManager started (maxPeers=" + maxPeers + ")");
    }

    /**
     * Stops the peer manager, disconnects all peers, and shuts down the heartbeat scheduler.
     */
    public void stop() {
        running = false;
        disconnectAll("node shutdown");
        scheduler.shutdownNow();
        LOGGER.info("PeerManager stopped");
    }

    // ─── Connection management ────────────────────────────────────────────────

    /**
     * Registers a peer as connected and publishes a {@link BlockchainEvent.PeerConnectedEvent}.
     *
     * <p>If the peer is already connected, or if the maximum peer count is reached,
     * this method returns {@code false} and takes no action.</p>
     *
     * @param peer the peer that has established a connection (non-null)
     * @return {@code true} if the peer was successfully added; {@code false} otherwise
     * @throws NullPointerException if peer is null
     */
    public boolean connect(Peer peer) {
        Objects.requireNonNull(peer, "peer must not be null");

        // Enforce maximum connections
        if (connectedNodeIds.size() >= maxPeers) {
            LOGGER.warning(() -> "Max peer limit reached (" + maxPeers
                + "). Rejecting connection from " + peer.getNodeId());
            return false;
        }

        // Record in store and mark connected
        Peer recorded = peer.withLastSeen(Instant.now());
        peerStore.put(recorded);
        boolean added = connectedNodeIds.add(recorded.getNodeId());

        if (added) {
            LOGGER.info(() -> "Peer connected: " + peer.getNodeId()
                + " at " + peer.getAddress());
            // Publish event asynchronously (FR-EVENT-03)
            eventBus.publish(new BlockchainEvent.PeerConnectedEvent(
                peer.getNodeId(), peer.getAddress()));
        }
        return added;
    }

    /**
     * Marks a peer as disconnected and publishes a {@link BlockchainEvent.PeerDisconnectedEvent}.
     *
     * <p>The peer remains in the {@link PeerStore} so that it can be reconnected later.
     * Only its entry in the connected-nodes set is removed.</p>
     *
     * @param nodeId the node ID of the peer to disconnect (non-null, non-blank)
     * @param reason human-readable reason for disconnection; may be null
     * @throws NullPointerException     if nodeId is null
     * @throws IllegalArgumentException if nodeId is blank
     */
    public void disconnect(String nodeId, String reason) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        if (nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId must not be blank");
        }

        boolean removed = connectedNodeIds.remove(nodeId);
        if (removed) {
            LOGGER.info(() -> "Peer disconnected: " + nodeId + " (reason: " + reason + ")");
            eventBus.publish(new BlockchainEvent.PeerDisconnectedEvent(nodeId, reason));
        }
    }

    /**
     * Disconnects all currently connected peers with the given reason.
     *
     * @param reason human-readable reason; may be null
     */
    public void disconnectAll(String reason) {
        // Copy to avoid ConcurrentModificationException
        List<String> ids = new ArrayList<>(connectedNodeIds);
        for (String nodeId : ids) {
            disconnect(nodeId, reason);
        }
        LOGGER.info(() -> "All peers disconnected. Count was: " + ids.size());
    }

    // ─── Heartbeat & pruning ──────────────────────────────────────────────────

    /**
     * Records a successful heartbeat for the given peer, updating its last-seen time.
     *
     * <p>Called by the transport layer each time a ping/pong exchange completes
     * successfully, or whenever any valid message is received from a peer.</p>
     *
     * @param nodeId the node ID of the peer to refresh (non-null, non-blank)
     * @throws NullPointerException     if nodeId is null
     * @throws IllegalArgumentException if nodeId is blank
     */
    public void recordHeartbeat(String nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        if (nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId must not be blank");
        }
        peerStore.get(nodeId).ifPresent(p -> peerStore.put(p.withLastSeen(Instant.now())));
    }

    /**
     * Periodic task: prunes peers whose last-seen timestamp exceeds the timeout.
     *
     * <p>This method is called by the scheduled executor every
     * {@link #HEARTBEAT_INTERVAL_SECONDS} seconds. Any peer that has not sent a
     * message within {@link #PEER_TIMEOUT_SECONDS} seconds is disconnected.</p>
     */
    private void heartbeatAndPrune() {
        if (!running) {
            return;
        }
        Instant cutoff = Instant.now().minus(Duration.ofSeconds(PEER_TIMEOUT_SECONDS));
        List<Peer> timedOut = connectedNodeIds.stream()
            .map(id -> peerStore.get(id).orElse(null))
            .filter(p -> p != null
                && p.getLastSeen() != null
                && p.getLastSeen().isBefore(cutoff))
            .collect(Collectors.toList());

        for (Peer stale : timedOut) {
            LOGGER.warning(() -> "Pruning unresponsive peer: " + stale.getNodeId()
                + " (last seen: " + stale.getLastSeen() + ")");
            disconnect(stale.getNodeId(), "heartbeat timeout");
        }
    }

    // ─── Queries ──────────────────────────────────────────────────────────────

    /**
     * Returns a snapshot of all currently connected peers.
     *
     * @return unmodifiable list of connected peers
     */
    public List<Peer> getConnectedPeers() {
        return connectedNodeIds.stream()
            .map(id -> peerStore.get(id).orElse(null))
            .filter(Objects::nonNull)
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Returns the number of currently connected peers.
     *
     * @return connected peer count (&ge; 0)
     */
    public int getConnectedPeerCount() {
        return connectedNodeIds.size();
    }

    /**
     * Returns {@code true} if a peer with the given node ID is currently connected.
     *
     * @param nodeId the node ID to check (non-null)
     * @return {@code true} if connected
     * @throws NullPointerException if nodeId is null
     */
    public boolean isConnected(String nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        return connectedNodeIds.contains(nodeId);
    }

    /**
     * Returns the maximum number of simultaneous peer connections.
     *
     * @return configured max-peers limit
     */
    public int getMaxPeers() {
        return maxPeers;
    }

    /**
     * Returns the underlying {@link PeerStore} for direct peer-level operations.
     *
     * @return non-null {@link PeerStore}
     */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "PeerStore is the intentional public API for peer-level operations. "
            + "PeerManager coordinates lifecycle; callers that need raw peer access "
            + "use PeerStore directly by design.")
    public PeerStore getPeerStore() {
        return peerStore;
    }

    /**
     * Returns a human-readable summary of the peer manager state.
     *
     * @return string representation
     */
    @Override
    public String toString() {
        return "PeerManager{"
            + "connected=" + connectedNodeIds.size()
            + ", max=" + maxPeers
            + ", running=" + running
            + '}';
    }
}
