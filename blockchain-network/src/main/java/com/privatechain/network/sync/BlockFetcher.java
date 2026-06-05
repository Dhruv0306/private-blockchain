package com.privatechain.network.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.privatechain.core.model.Block;
import com.privatechain.network.peer.Peer;
import com.privatechain.network.rpc.MessageCodec;
import com.privatechain.network.rpc.NodeClient;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

/**
 * Fetches missing blocks from a specific remote peer during chain synchronization.
 *
 * <p>{@code BlockFetcher} is used by {@link SyncManager} to download blocks
 * that the local node is missing. It sends {@code GET_BLOCKS} requests to a
 * chosen peer and returns the decoded block list for validation and persistence.</p>
 *
 * <h2>Batch size</h2>
 * <p>Block fetching is chunked into batches of at most {@link #BATCH_SIZE} blocks
 * to avoid creating excessively large TCP messages. For large sync gaps the fetcher
 * issues multiple requests until all blocks are obtained.</p>
 *
 * <h2>Thread safety</h2>
 * <p>Instances are stateless and safe for concurrent use once constructed.</p>
 *
 * @see SyncManager
 * @see NodeClient
 * @since 1.0.0
 */
public final class BlockFetcher {

    /**
     * Maximum number of blocks requested in a single GET_BLOCKS message.
     */
    public static final int BATCH_SIZE = 100;
    private static final Logger LOGGER = Logger.getLogger(BlockFetcher.class.getName());

    // ─── Fields ───────────────────────────────────────────────────────────────
    /**
     * This node's stable identifier (used as senderId in outbound messages).
     */
    private final String localNodeId;

    /**
     * Outbound TCP client for sending GET_BLOCKS requests.
     */
    private final NodeClient nodeClient;

    /**
     * Message codec for constructing GET_BLOCKS messages.
     */
    private final MessageCodec codec;

    /**
     * Jackson mapper for deserializing block payloads from responses.
     */
    private final ObjectMapper mapper;

    // ─── Constructor ──────────────────────────────────────────────────────────

    /**
     * Constructs a {@code BlockFetcher}.
     *
     * @param localNodeId this node's stable logical identifier (non-null, non-blank)
     * @param nodeClient  outbound connection client (non-null)
     * @param codec       message codec (non-null)
     * @throws NullPointerException     if any parameter is null
     * @throws IllegalArgumentException if localNodeId is blank
     */
    public BlockFetcher(String localNodeId, NodeClient nodeClient, MessageCodec codec) {
        Objects.requireNonNull(localNodeId, "localNodeId must not be null");
        if (localNodeId.isBlank()) {
            throw new IllegalArgumentException("localNodeId must not be blank");
        }
        this.localNodeId = localNodeId;
        this.nodeClient = Objects.requireNonNull(nodeClient, "nodeClient must not be null");
        this.codec = Objects.requireNonNull(codec, "codec must not be null");
        // Must match BlockSerializer.MAPPER: Block deserialization requires
        // FAIL_ON_UNKNOWN_PROPERTIES disabled and field-level visibility
        ObjectMapper m = new ObjectMapper();
        m.registerModule(new JavaTimeModule());
        m.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        m.disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        m.setVisibility(com.fasterxml.jackson.annotation.PropertyAccessor.GETTER,
            com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE);
        m.setVisibility(com.fasterxml.jackson.annotation.PropertyAccessor.IS_GETTER,
            com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE);
        m.setVisibility(com.fasterxml.jackson.annotation.PropertyAccessor.FIELD,
            com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY);
        this.mapper = m;
    }

    // ─── Fetching ─────────────────────────────────────────────────────────────

    /**
     * Fetches all blocks from {@code fromHeight} (inclusive) to {@code toHeight} (inclusive)
     * from the given peer, in ascending order.
     *
     * <p>Blocks are fetched in batches of at most {@link #BATCH_SIZE}. If any batch
     * fails to download, the method returns whatever blocks were collected before
     * the failure (partial result). The caller ({@link SyncManager}) is responsible
     * for detecting gaps and retrying if needed.</p>
     *
     * @param peer       the peer to fetch from (non-null)
     * @param fromHeight the first block height to request (&ge; 0)
     * @param toHeight   the last block height to request (&ge; fromHeight)
     * @return ordered list of fetched blocks; empty if the peer does not respond
     * @throws NullPointerException     if peer is null
     * @throws IllegalArgumentException if fromHeight &gt; toHeight
     */
    public List<Block> fetchBlocks(Peer peer, int fromHeight, int toHeight) {
        Objects.requireNonNull(peer, "peer must not be null");
        if (fromHeight > toHeight) {
            throw new IllegalArgumentException(
                "fromHeight (" + fromHeight + ") must be <= toHeight (" + toHeight + ")");
        }

        List<Block> result = new ArrayList<>();
        int current = fromHeight;

        while (current <= toHeight) {
            int batchEnd = Math.min(current + BATCH_SIZE - 1, toHeight);
            int finalCurrent = current;
            LOGGER.fine(() -> "Fetching blocks " + finalCurrent + ".." + batchEnd
                + " from peer " + peer.getNodeId());

            List<Block> batch = fetchBatch(peer, current, batchEnd);
            if (batch.isEmpty()) {
                int finalCurrent1 = current;
                LOGGER.warning(() -> "Peer " + peer.getNodeId()
                    + " returned no blocks for range " + finalCurrent1 + ".." + batchEnd
                    + " — aborting sync");
                break;
            }
            result.addAll(batch);
            current = batchEnd + 1;
        }

        LOGGER.info(() -> "BlockFetcher: received " + result.size() + " block(s) from "
            + peer.getNodeId() + " (requested " + fromHeight + ".." + toHeight + ")");
        return Collections.unmodifiableList(result);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Sends a single {@code GET_BLOCKS} request to the peer and decodes the response.
     *
     * <p>The response envelope is expected to carry a {@code BLOCK} message whose
     * payload is a JSON array of {@link Block} objects. Returns an empty list on
     * any network or parse error.</p>
     *
     * @param peer       the target peer (non-null)
     * @param fromHeight inclusive start height for this batch
     * @param toHeight   inclusive end height for this batch
     * @return decoded block list; empty on error
     */
    private List<Block> fetchBatch(Peer peer, int fromHeight, int toHeight) {
        MessageCodec.NetworkMessage request = codec.getBlocksMessage(localNodeId, fromHeight, toHeight);
        Optional<MessageCodec.NetworkMessage> response = nodeClient.sendAndReceive(peer, request);

        if (response.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            // The response payload is expected to be a JSON array of Block objects
            String payload = response.get().getPayload();
            Block[] blocks = mapper.readValue(payload, Block[].class);
            return List.of(blocks);
        } catch (IOException e) {
            LOGGER.warning(() -> "Failed to parse blocks response from " + peer.getNodeId()
                + ": " + e.getMessage());
            return Collections.emptyList();
        }
    }
}
