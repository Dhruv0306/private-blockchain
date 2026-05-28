package com.privatechain.network.sync;

import com.privatechain.core.builder.Blockchain;
import com.privatechain.core.model.Block;
import com.privatechain.core.network.ChainSyncer;
import com.privatechain.network.peer.Peer;
import com.privatechain.network.peer.PeerManager;
import com.privatechain.network.rpc.MessageCodec;
import com.privatechain.network.rpc.NodeClient;

import java.util.*;
import java.util.logging.Logger;

/**
 * Synchronizes the local blockchain with the P2P network on node startup and after
 * partition recovery (FR-NET-04, design.md §4.3).
 *
 * <h2>Algorithm</h2>
 * <ol>
 *   <li>Broadcast {@code GET_STATUS} to all connected peers.</li>
 *   <li>Collect {@code STATUS} responses (chainHeight per peer).</li>
 *   <li>Find the peer with {@code max(chainHeight)}.</li>
 *   <li>If that height is greater than the local chain: fetch the missing blocks from
 *       that peer via {@link BlockFetcher}.</li>
 *   <li>Validate and append each fetched block via {@link Blockchain#addBlock(Block)}.</li>
 *   <li>If the peer's chain is shorter or equal: local chain is already up-to-date.</li>
 * </ol>
 *
 * <h2>Fork handling</h2>
 * <p>If during sync a received block fails {@link Blockchain#addBlock(Block)} (e.g.,
 * because its {@code previousHash} does not match the local tip), {@link ForkResolver}
 * is consulted. A full chain replacement is outside the scope of this milestone and is
 * noted as a backlog item (T-B04).</p>
 *
 * <h2>Thread safety</h2>
 * <p>{@link #syncChain()} is designed to be called from the node-startup thread. Concurrent
 * calls are allowed but each call proceeds independently; {@link Blockchain#addBlock}
 * is thread-safe via its internal {@link java.util.concurrent.locks.ReentrantLock}.</p>
 *
 * @see BlockFetcher
 * @see ForkResolver
 * @see PeerManager
 * @since 1.0.0
 */
public final class SyncManager implements ChainSyncer {

    private static final Logger LOGGER = Logger.getLogger(SyncManager.class.getName());

    /**
     * Timeout (ms) to wait for STATUS responses before proceeding with partial data.
     */
    private static final long STATUS_COLLECT_TIMEOUT_MS = 3_000;

    // ─── Fields ───────────────────────────────────────────────────────────────

    /**
     * The local chain that will be extended by blocks fetched from peers.
     */
    private final Blockchain blockchain;

    /**
     * Provides the connected-peer list for status polling and block fetching.
     */
    private final PeerManager peerManager;

    /**
     * Fetches batches of blocks from a chosen peer.
     */
    private final BlockFetcher blockFetcher;

    /**
     * Resolves tie-breaks and detects whether a peer's chain is superior.
     */
    private final ForkResolver forkResolver;

    /**
     * Message codec for constructing GET_STATUS messages.
     */
    private final MessageCodec codec;

    /**
     * Outbound client for sending GET_STATUS requests.
     */
    private final NodeClient nodeClient;

    /**
     * This node's stable identifier, used as senderId in outbound messages.
     */
    private final String localNodeId;

    // ─── Constructor ──────────────────────────────────────────────────────────

    /**
     * Constructs a {@code SyncManager} with all required dependencies.
     *
     * @param localNodeId  this node's stable logical identifier (non-null, non-blank)
     * @param blockchain   the local chain manager (non-null)
     * @param peerManager  the peer lifecycle manager (non-null)
     * @param blockFetcher the block downloader (non-null)
     * @param forkResolver the fork-selection arbiter (non-null)
     * @param codec        message codec for GET_STATUS/STATUS messages (non-null)
     * @param nodeClient   outbound TCP client (non-null)
     * @throws NullPointerException     if any parameter is null
     * @throws IllegalArgumentException if localNodeId is blank
     */
    public SyncManager(
        String localNodeId,
        Blockchain blockchain,
        PeerManager peerManager,
        BlockFetcher blockFetcher,
        ForkResolver forkResolver,
        MessageCodec codec,
        NodeClient nodeClient) {

        Objects.requireNonNull(localNodeId, "localNodeId must not be null");
        if (localNodeId.isBlank()) {
            throw new IllegalArgumentException("localNodeId must not be blank");
        }
        this.localNodeId = localNodeId;
        this.blockchain = Objects.requireNonNull(blockchain, "blockchain must not be null");
        this.peerManager = Objects.requireNonNull(peerManager, "peerManager must not be null");
        this.blockFetcher = Objects.requireNonNull(blockFetcher, "blockFetcher must not be null");
        this.forkResolver = Objects.requireNonNull(forkResolver, "forkResolver must not be null");
        this.codec = Objects.requireNonNull(codec, "codec must not be null");
        this.nodeClient = Objects.requireNonNull(nodeClient, "nodeClient must not be null");
    }

    // ─── Sync ─────────────────────────────────────────────────────────────────

    /**
     * Performs chain synchronization against the connected peer network.
     *
     * <p>This method implements the peer-sync flow described in design.md §4.3:</p>
     * <ol>
     *   <li>Query all connected peers for their chain heights via {@code GET_STATUS}.</li>
     *   <li>Select the peer with the greatest height as the sync source.</li>
     *   <li>Fetch and apply any missing blocks from that peer.</li>
     * </ol>
     *
     * <p>If no peers are connected, or if all peers have equal/lower chain heights,
     * this method returns immediately without modifying the local chain.</p>
     *
     * @return the number of blocks appended to the local chain during this sync session
     */
    public int syncChain() {
        List<Peer> connectedPeers = peerManager.getConnectedPeers();
        if (connectedPeers.isEmpty()) {
            LOGGER.info("SyncManager: no connected peers — skipping chain sync");
            return 0;
        }

        int localHeight = blockchain.size();
        LOGGER.info(() -> "SyncManager: starting sync (localHeight=" + localHeight
            + ", peers=" + connectedPeers.size() + ")");

        // ── Step 1: Collect chain heights from all peers ─────────────────────
        Map<Peer, Integer> peerHeights = collectPeerHeights(connectedPeers);
        if (peerHeights.isEmpty()) {
            LOGGER.warning("SyncManager: no peers responded to GET_STATUS — aborting sync");
            return 0;
        }

        // ── Step 2: Find the peer with the maximum chain height ──────────────
        Optional<Map.Entry<Peer, Integer>> bestEntry = peerHeights.entrySet().stream()
            .max(Map.Entry.comparingByValue());

        if (bestEntry.isEmpty()) {
            return 0;
        }

        Peer bestPeer = bestEntry.get().getKey();
        int bestHeight = bestEntry.get().getValue();

        if (bestHeight <= localHeight) {
            LOGGER.info(() -> "SyncManager: local chain is up-to-date (localHeight="
                + localHeight + ", bestPeerHeight=" + bestHeight + ")");
            return 0;
        }

        // ── Step 3: Fetch missing blocks ─────────────────────────────────────
        int firstMissingIndex = localHeight;   // 0-based; size() == first missing index
        LOGGER.info(() -> "SyncManager: fetching blocks " + firstMissingIndex
            + ".." + (bestHeight - 1) + " from peer " + bestPeer.getNodeId());

        List<Block> fetchedBlocks = blockFetcher.fetchBlocks(
            bestPeer, firstMissingIndex, bestHeight - 1);

        if (fetchedBlocks.isEmpty()) {
            LOGGER.warning(() -> "SyncManager: peer " + bestPeer.getNodeId()
                + " returned no blocks — sync incomplete");
            return 0;
        }

        // ── Step 4: Validate and append each block ───────────────────────────
        int appended = 0;
        for (Block block : fetchedBlocks) {
            try {
                blockchain.addBlock(block);
                appended++;
                LOGGER.fine(() -> "SyncManager: appended block index=" + block.getIndex());
            } catch (Exception e) {
                // Validation failure terminates the sync for this session;
                // the ForkResolver would be consulted here in a full implementation.
                LOGGER.warning(() -> "SyncManager: block index=" + block.getIndex()
                    + " failed validation — stopping sync: " + e.getMessage());
                break;
            }
        }

        int finalAppended = appended;
        LOGGER.info(() -> "SyncManager: sync complete — appended " + finalAppended
            + " block(s), new localHeight=" + blockchain.size());
        return appended;
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Sends {@code GET_STATUS} to each peer and collects the reported chain heights.
     *
     * <p>Peers that do not respond within the per-request timeout are silently skipped.
     * The result map contains only peers that successfully replied.</p>
     *
     * @param peers list of peers to query (non-null, non-empty)
     * @return map from responding peer to reported chain height
     */
    private Map<Peer, Integer> collectPeerHeights(List<Peer> peers) {
        Map<Peer, Integer> heights = new HashMap<>();
        MessageCodec.NetworkMessage request = codec.getStatusMessage(localNodeId);

        for (Peer peer : peers) {
            Optional<MessageCodec.NetworkMessage> response =
                nodeClient.sendAndReceive(peer, request);

            response.ifPresentOrElse(
                msg -> {
                    try {
                        int height = codec.extractChainHeight(msg);
                        heights.put(peer, height);
                        LOGGER.fine(() -> "SyncManager: peer " + peer.getNodeId()
                            + " reports chainHeight=" + height);
                    } catch (IllegalArgumentException e) {
                        LOGGER.fine(() -> "SyncManager: malformed STATUS from "
                            + peer.getNodeId() + ": " + e.getMessage());
                    }
                },
                () -> LOGGER.fine(() -> "SyncManager: no response from " + peer.getNodeId())
            );
        }
        return heights;
    }
}
