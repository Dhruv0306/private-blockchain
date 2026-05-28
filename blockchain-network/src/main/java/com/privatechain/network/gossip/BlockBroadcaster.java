package com.privatechain.network.gossip;

import com.privatechain.core.event.BlockchainEvent;
import com.privatechain.core.event.BlockchainEventListener;
import com.privatechain.core.model.Block;
import com.privatechain.network.peer.Peer;
import com.privatechain.network.peer.PeerManager;
import com.privatechain.network.rpc.MessageCodec;
import com.privatechain.network.rpc.NodeClient;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Propagates newly mined or locally accepted blocks to all connected peers.
 *
 * <p>{@code BlockBroadcaster} implements {@link BlockchainEventListener} so that it
 * can be registered on the {@link com.privatechain.core.event.BlockchainEventBus}.
 * Whenever a {@link BlockchainEvent.BlockAddedEvent} is published — indicating that
 * a block has been appended to the local canonical chain — this class fans the block
 * out to every connected peer (FR-NET-02).</p>
 *
 * <h2>Fan-out strategy</h2>
 * <p>Unlike {@link GossipProtocol} (which sends to a random subset for transaction
 * propagation), the broadcaster sends to <em>all</em> connected peers. Blocks are
 * critical consensus data that every peer must receive promptly.</p>
 *
 * <h2>Deduplication</h2>
 * <p>Each outbound broadcast records the sending node's ID in the message envelope
 * (via the {@link MessageCodec#blockMessage} factory). Remote nodes that receive
 * the block will propagate it further via their own event listeners, but because the
 * {@code senderId} is echoed back, each node can avoid re-broadcasting a block it
 * received from the network (loop prevention at the application layer).</p>
 *
 * <h2>Thread safety</h2>
 * <p>Broadcasts are submitted to a cached thread pool, so multiple blocks can be
 * fanned out concurrently without blocking the event bus delivery thread.</p>
 *
 * @see GossipProtocol
 * @see NodeClient
 * @since 1.0.0
 */
public final class BlockBroadcaster implements BlockchainEventListener {

    private static final Logger LOGGER = Logger.getLogger(BlockBroadcaster.class.getName());

    // ─── Fields ───────────────────────────────────────────────────────────────

    /**
     * This node's stable identifier, included in outbound message envelopes.
     */
    private final String localNodeId;

    /**
     * Provides the list of connected peers to broadcast to.
     */
    private final PeerManager peerManager;

    /**
     * Outbound connection factory.
     */
    private final NodeClient nodeClient;

    /**
     * Codec for creating BLOCK message envelopes.
     */
    private final MessageCodec codec;

    /**
     * Dedicated thread pool for concurrent fan-out.
     * Using a cached pool so broadcast latency is bounded by the slowest peer, not
     * by the number of peers (each peer send runs on its own thread).
     */
    private final ExecutorService broadcastPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "block-broadcaster");
        t.setDaemon(true);
        return t;
    });

    // ─── Constructor ──────────────────────────────────────────────────────────

    /**
     * Constructs a {@code BlockBroadcaster}.
     *
     * @param localNodeId this node's stable logical identifier (non-null, non-blank)
     * @param peerManager the peer manager for obtaining the connected-peer list (non-null)
     * @param nodeClient  the outbound connection client (non-null)
     * @param codec       the message codec (non-null)
     * @throws NullPointerException     if any parameter is null
     * @throws IllegalArgumentException if localNodeId is blank
     */
    public BlockBroadcaster(
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
     * Reacts to blockchain events, broadcasting new blocks to all connected peers.
     *
     * <p>Only {@link BlockchainEvent.BlockAddedEvent} instances trigger a broadcast;
     * all other event types are ignored silently.</p>
     *
     * @param event the blockchain event to handle (non-null)
     */
    @Override
    public void onEvent(BlockchainEvent event) {
        if (event instanceof BlockchainEvent.BlockAddedEvent blockEvent) {
            broadcast(blockEvent.getBlock());
        }
        // All other event types are irrelevant to block broadcasting.
    }

    // ─── Broadcast ────────────────────────────────────────────────────────────

    /**
     * Fans out the given block to all currently connected peers.
     *
     * <p>Each peer receives its own message on an independent thread so that a
     * slow or unresponsive peer cannot stall delivery to the others.
     * The broadcast pool is daemon-threaded, so in-flight sends are aborted
     * on JVM exit.</p>
     *
     * @param block the block to broadcast (non-null)
     * @throws NullPointerException if block is null
     */
    public void broadcast(Block block) {
        Objects.requireNonNull(block, "block must not be null");

        List<Peer> peers = peerManager.getConnectedPeers();
        if (peers.isEmpty()) {
            LOGGER.fine(() -> "No connected peers — skipping block broadcast for index "
                + block.getIndex());
            return;
        }

        MessageCodec.NetworkMessage message = codec.blockMessage(localNodeId, block);
        LOGGER.info(() -> "Broadcasting block index=" + block.getIndex()
            + " to " + peers.size() + " peer(s)");

        // Fan-out: submit one send task per peer (FR-NET-02)
        for (Peer peer : peers) {
            broadcastPool.submit(() -> {
                boolean sent = nodeClient.send(peer, message);
                if (!sent) {
                    LOGGER.fine(() -> "Block broadcast failed for peer " + peer.getNodeId());
                }
            });
        }
    }

    /**
     * Shuts down the broadcast thread pool.
     *
     * <p>In-flight broadcasts that have already been submitted will complete.
     * New broadcasts submitted after this call will be rejected.</p>
     */
    public void shutdown() {
        broadcastPool.shutdownNow();
        LOGGER.info("BlockBroadcaster shutdown");
    }
}
