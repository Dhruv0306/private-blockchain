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
 * <p>Responsibilities (FR-NET-01):</p>
 * <ul>
 *   <li>Maintaining the set of connected peer node IDs via {@link CopyOnWriteArraySet}</li>
 *   <li>Persisting peer metadata to the {@link PeerStore} on connect/disconnect</li>
 *   <li>Running a periodic heartbeat to prune unresponsive peers</li>
 *   <li>Publishing {@link BlockchainEvent.PeerConnectedEvent} and
 *       {@link BlockchainEvent.PeerDisconnectedEvent} to the shared
 *       {@link BlockchainEventBus} (T-066, Milestone 8)</li>
 * </ul>
 *
 * <h2>Milestone 8 wiring (T-066)</h2>
 * <p>Every successful {@link #connect(Peer)} publishes a
 * {@link BlockchainEvent.PeerConnectedEvent} and every
 * {@link #disconnect(String, String)} publishes a
 * {@link BlockchainEvent.PeerDisconnectedEvent} asynchronously.</p>
 *
 * <h2>Design note</h2>
 * <p>This class coordinates lifecycle — actual TCP I/O is delegated to
 * {@code NodeServer} and {@code NodeClient} in the transport layer.</p>
 *
 * @see Peer
 * @see PeerStore
 * @since 1.0.0
 */
public final class PeerManager implements PeerManagerLifecycle {

    /**
     * How often the heartbeat task checks peer liveness (seconds).
     */
    public static final int HEARTBEAT_INTERVAL_SECONDS = 30;
    /**
     * Silence duration before a peer is considered unresponsive (seconds).
     */
    public static final int PEER_TIMEOUT_SECONDS = 90;
    private static final Logger LOGGER = Logger.getLogger(PeerManager.class.getName());

    // ─── Dependencies ─────────────────────────────────────────────────────────
    /**
     * Persistent index of all known peers (connected and disconnected).
     * {@code PeerStore.put(Peer)} is the correct method to save a peer.
     */
    private final PeerStore peerStore;

    /**
     * Ordered set of nodeIds currently considered connected.
     * {@link CopyOnWriteArraySet} provides lock-free reads during message fan-out.
     */
    private final Set<String> connectedNodeIds = new CopyOnWriteArraySet<>();

    /**
     * Maximum number of simultaneous peer connections (FR-NET-07).
     */
    private final int maxPeers;

    /**
     * Event bus for publishing peer lifecycle events (T-066, Milestone 8).
     */
    private final BlockchainEventBus eventBus;

    // ─── Scheduler ────────────────────────────────────────────────────────────

    /**
     * Scheduler running the periodic heartbeat/pruning task.
     * Daemon thread prevents the scheduler from blocking JVM shutdown.
     */
    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "peer-heartbeat");
            t.setDaemon(true);
            return t;
        });

    /**
     * Guards the running state to prevent double-start.
     */
    private volatile boolean running = false;

    // ─── Constructor ──────────────────────────────────────────────────────────

    /**
     * Constructs a {@code PeerManager} with all required dependencies.
     *
     * @param peerStore the backing peer store (non-null)
     * @param eventBus  event bus for publishing peer lifecycle events (non-null)
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

    // ─── PeerManagerLifecycle ─────────────────────────────────────────────────

    /**
     * Starts the peer manager and schedules the periodic heartbeat/pruning task.
     *
     * <p>This method is idempotent; calling it while already running is a no-op.</p>
     */
    @Override
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
        LOGGER.info(() -> "PeerManager started [maxPeers=" + maxPeers + "]");
    }

    /**
     * Stops the peer manager, disconnects all peers, and shuts down the heartbeat scheduler.
     */
    @Override
    public void stop() {
        running = false;
        disconnectAll("node shutdown");
        scheduler.shutdownNow();
        LOGGER.info("PeerManager stopped");
    }

    /**
     * Returns the number of currently connected peers.
     *
     * @return connected peer count (&ge; 0)
     */
    @Override
    public int getConnectedPeerCount() {
        return connectedNodeIds.size();
    }

    // ─── Connection management ────────────────────────────────────────────────

    /**
     * Registers a peer as connected and publishes a
     * {@link BlockchainEvent.PeerConnectedEvent} (T-066).
     *
     * <p>If the peer is already connected, or if the max peer count is reached,
     * this method returns {@code false} and takes no action. On success the peer
     * is persisted to the {@link PeerStore} with a fresh {@code lastSeen} timestamp
     * via {@link PeerStore#put(Peer)}.</p>
     *
     * @param peer the peer that has established a connection (non-null)
     * @return {@code true} if the peer was newly added; {@code false} otherwise
     * @throws NullPointerException if peer is null
     */
    public boolean connect(Peer peer) {
        Objects.requireNonNull(peer, "peer must not be null");

        if (connectedNodeIds.size() >= maxPeers) {
            LOGGER.warning(() ->
                "Max peer limit reached (" + maxPeers
                    + "). Rejecting connection from " + peer.getNodeId());
            return false;
        }

        // Stamp with current time and persist via PeerStore.put() (correct API)
        Peer recorded = peer.withLastSeen(Instant.now());
        peerStore.put(recorded);                         // ← PeerStore.put(), not .save()
        boolean added = connectedNodeIds.add(recorded.getNodeId());

        if (added) {
            LOGGER.info(() ->
                "Peer connected [nodeId=" + peer.getNodeId()
                    + ", address=" + peer.getAddress()
                    + ", totalPeers=" + connectedNodeIds.size() + "]");

            // Publish event asynchronously (T-066, Milestone 8, FR-EVENT-03)
            publishPeerConnectedEvent(peer);
        }
        return added;
    }

    /**
     * Marks a peer as disconnected and publishes a
     * {@link BlockchainEvent.PeerDisconnectedEvent} (T-066).
     *
     * <p>The peer remains in the {@link PeerStore} for potential reconnection.
     * Only its entry in the connected-nodes set is removed.</p>
     *
     * @param nodeId the node ID of the peer to disconnect (non-null, non-blank)
     * @param reason human-readable reason; may be {@code null}
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
            LOGGER.info(() ->
                "Peer disconnected [nodeId=" + nodeId
                    + ", reason=" + (reason != null ? reason : "unknown") + "]");

            // Publish event asynchronously (T-066, Milestone 8)
            publishPeerDisconnectedEvent(nodeId, reason);
        }
    }

    /**
     * Disconnects all currently connected peers with the given reason.
     *
     * @param reason human-readable reason; may be {@code null}
     */
    public void disconnectAll(String reason) {
        List<String> ids = new ArrayList<>(connectedNodeIds);
        for (String nodeId : ids) {
            disconnect(nodeId, reason);
        }
        LOGGER.info(() -> "All " + ids.size() + " peer(s) disconnected [reason=" + reason + "]");
    }

    // ─── Heartbeat recording ──────────────────────────────────────────────────

    /**
     * Records a successful heartbeat for the given peer, refreshing its
     * {@code lastSeen} timestamp in the {@link PeerStore}.
     *
     * <p>Called by the transport layer whenever a ping/pong or any valid inbound
     * message is received from the peer.</p>
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
        // PeerStore.get() returns Optional<Peer>; use withLastSeen() then put()
        peerStore.get(nodeId)
            .ifPresent(p -> peerStore.put(p.withLastSeen(Instant.now())));
    }

    // ─── Queries ──────────────────────────────────────────────────────────────

    /**
     * Returns a snapshot list of all currently connected {@link Peer} instances.
     *
     * @return unmodifiable list of connected peers; never null
     */
    public List<Peer> getConnectedPeers() {
        return connectedNodeIds.stream()
            .map(id -> peerStore.get(id).orElse(null))
            .filter(Objects::nonNull)
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Returns {@code true} if the peer with the given node ID is currently connected.
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
     * Returns the backing {@link PeerStore} for direct peer-level operations.
     *
     * @return non-null {@link PeerStore}
     */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "PeerStore is the intentional public API for peer-level ops.")
    public PeerStore getPeerStore() {
        return peerStore;
    }

    // ─── Heartbeat pruning ────────────────────────────────────────────────────

    /**
     * Periodic task: prunes peers whose {@code lastSeen} timestamp exceeds the timeout.
     *
     * <p>Uses {@link Peer#getLastSeen()} which returns an {@link Instant} (not millis),
     * consistent with the actual {@link Peer} API. Peers are pruned with reason
     * {@code "heartbeat timeout"} and a {@link BlockchainEvent.PeerDisconnectedEvent}
     * is published for each.</p>
     */
    private void heartbeatAndPrune() {
        if (!running) {
            return;
        }
        // Cutoff: peers not seen within PEER_TIMEOUT_SECONDS are stale
        Instant cutoff = Instant.now().minus(Duration.ofSeconds(PEER_TIMEOUT_SECONDS));

        // Collect stale peers using Peer.getLastSeen() (returns Instant, not long)
        List<Peer> timedOut = connectedNodeIds.stream()
            .map(id -> peerStore.get(id).orElse(null))
            .filter(p -> p != null
                && p.getLastSeen() != null          // ← Peer.getLastSeen() returns Instant
                && p.getLastSeen().isBefore(cutoff))
            .collect(Collectors.toList());

        for (Peer stale : timedOut) {
            LOGGER.warning(() ->
                "Pruning unresponsive peer [nodeId=" + stale.getNodeId()
                    + ", lastSeen=" + stale.getLastSeen() + "]");
            disconnect(stale.getNodeId(), "heartbeat timeout");
        }
    }

    // ─── Event publishing (T-066) ─────────────────────────────────────────────

    /**
     * Publishes a {@link BlockchainEvent.PeerConnectedEvent} if the event bus is live.
     *
     * @param peer the newly connected peer
     */
    private void publishPeerConnectedEvent(Peer peer) {
        if (!eventBus.isShutdown()) {
            eventBus.publish(
                new BlockchainEvent.PeerConnectedEvent(
                    peer.getNodeId(), peer.getAddress()));
        }
    }

    /**
     * Publishes a {@link BlockchainEvent.PeerDisconnectedEvent} if the event bus is live.
     *
     * @param nodeId the ID of the disconnected peer
     * @param reason disconnection reason; may be {@code null}
     */
    private void publishPeerDisconnectedEvent(String nodeId, String reason) {
        if (!eventBus.isShutdown()) {
            eventBus.publish(
                new BlockchainEvent.PeerDisconnectedEvent(nodeId, reason));
        }
    }

    // ─── Object overrides ─────────────────────────────────────────────────────

    /**
     * Returns a human-readable summary of the peer manager state.
     *
     * @return string representation
     */
    @Override
    public String toString() {
        return "PeerManager{connected=" + connectedNodeIds.size()
            + ", max=" + maxPeers
            + ", running=" + running + '}';
    }
}
