package com.privatechain.network.gossip;

import com.privatechain.core.event.BlockchainEvent;
import com.privatechain.core.event.BlockchainEventListener;
import com.privatechain.core.model.Transaction;
import com.privatechain.network.peer.Peer;
import com.privatechain.network.peer.PeerManager;
import com.privatechain.network.rpc.MessageCodec;
import com.privatechain.network.rpc.NodeClient;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Propagates submitted transactions to a random subset of connected peers via epidemic gossip.
 *
 * <p>{@code GossipProtocol} implements {@link BlockchainEventListener} so that it can be
 * registered on the event bus. When a {@link BlockchainEvent.TransactionSubmittedEvent} is
 * published, this class selects {@code ceil(log₂(n))} random peers and forwards the
 * transaction to each (FR-NET-03).</p>
 *
 * <h2>Fan-out size</h2>
 * <p>Using {@code ⌈log₂(n)⌉} as the fan-out grows slowly with the network size,
 * providing sub-logarithmic propagation time with high probability (analogous to
 * Ethereum's discovery). For a 25-peer network this yields 5 initial recipients.</p>
 *
 * <h2>Loop prevention</h2>
 * <p>The originating sender ID is stamped into the message envelope. Recipients
 * compare the {@code senderId} against their own node ID and against a bounded
 * set of recently seen transaction IDs to avoid re-gossiping what they just received.
 * The seen-set is maintained in a fixed-size {@link LinkedHashMap} LRU cache.</p>
 *
 * <h2>Direct gossip API</h2>
 * <p>In addition to the event-listener path, callers (e.g., {@link com.privatechain.network.rpc.NodeServer})
 * can call {@link #gossip(Transaction, String)} directly when processing an inbound
 * transaction message that needs to be forwarded.</p>
 *
 * <h2>Thread safety</h2>
 * <p>All public methods are safe for concurrent use. The seen-set is synchronized;
 * peer selection uses an immutable snapshot of the peer list; gossip sends are
 * dispatched to a cached thread pool.</p>
 *
 * @see BlockBroadcaster
 * @see NodeClient
 * @since 1.0.0
 */
public final class GossipProtocol implements BlockchainEventListener {

    private static final Logger LOGGER = Logger.getLogger(GossipProtocol.class.getName());

    /**
     * Maximum number of recently-seen transaction IDs kept to prevent re-gossip loops.
     * Once this limit is reached, the oldest entries are evicted (LRU).
     */
    private static final int SEEN_CACHE_MAX = 10_000;

    // ─── Fields ───────────────────────────────────────────────────────────────

    /**
     * This node's stable identifier; used as the {@code senderId} in outbound envelopes.
     */
    private final String localNodeId;

    /**
     * Provides the current connected-peer list for gossip target selection.
     */
    private final PeerManager peerManager;

    /**
     * Outbound client for sending TRANSACTION messages.
     */
    private final NodeClient nodeClient;

    /**
     * Codec for constructing TRANSACTION message envelopes.
     */
    private final MessageCodec codec;

    /**
     * Cryptographically secure RNG for random peer selection (NFR-SEC-05).
     * {@link SecureRandom} is thread-safe after construction.
     */
    private final SecureRandom rng = new SecureRandom();

    /**
     * LRU cache of recently seen transaction IDs to suppress duplicate gossip.
     * Synchronized externally via {@code synchronized(seenTransactions)}.
     */
    private final Map<String, Boolean> seenTransactions =
        new LinkedHashMap<>(SEEN_CACHE_MAX, 0.75f, /* accessOrder= */ true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > SEEN_CACHE_MAX;
            }
        };

    /**
     * Dedicated thread pool for concurrent gossip sends.
     */
    private final ExecutorService gossipPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "gossip-protocol");
        t.setDaemon(true);
        return t;
    });

    // ─── Constructor ──────────────────────────────────────────────────────────

    /**
     * Constructs a {@code GossipProtocol} with the required dependencies.
     *
     * @param localNodeId this node's stable logical identifier (non-null, non-blank)
     * @param peerManager provides connected peers for gossip target selection (non-null)
     * @param nodeClient  outbound connection factory (non-null)
     * @param codec       message encoder (non-null)
     * @throws NullPointerException     if any parameter is null
     * @throws IllegalArgumentException if localNodeId is blank
     */
    public GossipProtocol(
        String localNodeId,
        PeerManager peerManager,
        NodeClient nodeClient,
        MessageCodec codec) {

        Objects.requireNonNull(localNodeId, "localNodeId must not be null");
        if (localNodeId.isBlank()) {
            throw new IllegalArgumentException("localNodeId must not be blank");
        }
        this.localNodeId = localNodeId;
        this.peerManager = Objects.requireNonNull(peerManager, "peerManager must not be null");
        this.nodeClient = Objects.requireNonNull(nodeClient, "nodeClient must not be null");
        this.codec = Objects.requireNonNull(codec, "codec must not be null");
    }

    // ─── BlockchainEventListener ──────────────────────────────────────────────

    /**
     * Reacts to blockchain events.
     *
     * <p>Only {@link BlockchainEvent.TransactionSubmittedEvent} instances trigger gossip;
     * all other events are ignored. The originating sender is assumed to be the local
     * node when called via the event bus (i.e., the transaction was submitted locally).</p>
     *
     * @param event the blockchain event to process (non-null)
     */
    @Override
    public void onEvent(BlockchainEvent event) {
        if (event instanceof BlockchainEvent.TransactionSubmittedEvent txEvent) {
            gossip(txEvent.getTransaction(), localNodeId);
        }
    }

    // ─── Gossip ───────────────────────────────────────────────────────────────

    /**
     * Gossips a transaction to {@code ⌈log₂(n)⌉} randomly selected connected peers.
     *
     * <p>This method is idempotent for duplicate transaction IDs — if the same
     * transaction has been gossiped recently it is silently skipped.
     * The originator ({@code originSenderId}) is excluded from the recipient set
     * so that a transaction is never echoed back to the node that sent it.</p>
     *
     * @param transaction    the transaction to propagate (non-null)
     * @param originSenderId the node ID that originally submitted this transaction;
     *                       excluded from the recipient set (non-null, non-blank)
     * @throws NullPointerException     if transaction or originSenderId is null
     * @throws IllegalArgumentException if originSenderId is blank
     */
    public void gossip(Transaction transaction, String originSenderId) {
        Objects.requireNonNull(transaction, "transaction must not be null");
        Objects.requireNonNull(originSenderId, "originSenderId must not be null");
        if (originSenderId.isBlank()) {
            throw new IllegalArgumentException("originSenderId must not be blank");
        }

        // Loop prevention: skip if already gossiped recently
        String txId = transaction.getId().toString();
        synchronized (seenTransactions) {
            if (seenTransactions.containsKey(txId)) {
                LOGGER.fine(() -> "Skipping duplicate gossip for transaction " + txId);
                return;
            }
            seenTransactions.put(txId, Boolean.TRUE);
        }

        // Select random subset of connected peers, excluding the originator
        List<Peer> candidates = peerManager.getConnectedPeers().stream()
            .filter(p -> !p.getNodeId().equals(originSenderId))
            .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            LOGGER.fine(() -> "No eligible peers for gossip of transaction " + txId);
            return;
        }

        // Fan-out: ceil(log2(n)) peers (FR-NET-03)
        int fanOut = computeFanOut(candidates.size());
        List<Peer> targets = selectRandom(candidates, fanOut);

        MessageCodec.NetworkMessage message = codec.transactionMessage(localNodeId, transaction);
        LOGGER.fine(() -> "Gossiping transaction " + txId + " to " + targets.size()
            + " of " + candidates.size() + " eligible peers (fanOut=" + fanOut + ")");

        for (Peer target : targets) {
            gossipPool.submit(() -> {
                boolean sent = nodeClient.send(target, message);
                if (!sent) {
                    LOGGER.fine(() -> "Gossip failed for peer " + target.getNodeId()
                        + " tx=" + txId);
                }
            });
        }
    }

    /**
     * Shuts down the gossip thread pool.
     *
     * <p>In-flight sends that have already been submitted will be interrupted.
     * New gossip calls submitted after this point are rejected.</p>
     */
    public void shutdown() {
        gossipPool.shutdownNow();
        LOGGER.info("GossipProtocol shutdown");
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Computes {@code ⌈log₂(n)⌉} — the gossip fan-out for {@code n} eligible peers.
     *
     * <p>Always returns at least 1, even if {@code n == 1}.</p>
     *
     * @param n number of eligible peers (&ge; 1)
     * @return fan-out count (&ge; 1)
     */
    public static int computeFanOut(int n) {
        if (n <= 1) return 1;
        return (int) Math.ceil(Math.log(n) / Math.log(2));
    }

    /**
     * Selects up to {@code count} peers at random from the candidate list.
     *
     * <p>Uses a partial Fisher-Yates shuffle to pick exactly {@code min(count, candidates.size())}
     * peers in O(count) time without modifying the original list.</p>
     *
     * @param candidates the peer pool to sample from (non-null, non-empty)
     * @param count      how many peers to select
     * @return an unmodifiable list of selected peers
     */
    private List<Peer> selectRandom(List<Peer> candidates, int count) {
        int available = candidates.size();
        int select = Math.min(count, available);

        // Defensive copy so we can swap elements for the shuffle
        List<Peer> pool = new ArrayList<>(candidates);
        List<Peer> selected = new ArrayList<>(select);

        for (int i = 0; i < select; i++) {
            int j = i + rng.nextInt(available - i); // random index in remaining pool
            // Swap i ↔ j
            Peer tmp = pool.get(i);
            pool.set(i, pool.get(j));
            pool.set(j, tmp);
            selected.add(pool.get(i));
        }
        return Collections.unmodifiableList(selected);
    }
}
