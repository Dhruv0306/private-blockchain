package com.privatechain.spring;

import com.privatechain.core.builder.BlockchainNode;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Provides blockchain health and status details as a plain Spring bean.
 *
 * <p>This class intentionally does <em>not</em> implement
 * {@code org.springframework.boot.actuate.health.HealthIndicator} so that
 * {@code blockchain-spring} compiles without a mandatory dependency on any
 * particular Spring Boot Actuator version. The full health payload is exposed
 * via {@link #getDetails()} and {@link #isUp()}, making it trivial for
 * consuming applications to bridge to whichever Actuator API they use:</p>
 *
 * <pre>{@code
 * // In your @Configuration class (spring-boot-actuator on YOUR classpath):
 * @Bean
 * HealthIndicator blockchainHealth(BlockchainHealthIndicator indicator) {
 *     return () -> indicator.isUp()
 *         ? Health.up().withDetails(indicator.getDetails()).build()
 *         : Health.down().withDetails(indicator.getDetails()).build();
 * }
 * }</pre>
 *
 * <h2>Sample detail map</h2>
 * <pre>{@code
 * {
 *   "status":         "UP",
 *   "chainHeight":    42,
 *   "mempoolSize":    3,
 *   "peerCount":      2,
 *   "consensusEngine":"ProofOfWork",
 *   "lastBlockTime":  "2026-06-05T10:15:30Z"
 * }
 * }</pre>
 *
 * @see BlockchainAutoConfiguration
 * @see BlockchainNode.NodeStatus
 * @since 1.0.0
 */
public class BlockchainHealthIndicator {

    /**
     * Detail key: overall UP / DOWN status string.
     */
    public static final String KEY_STATUS = "status";

    /**
     * Detail key: current chain height (number of blocks including genesis).
     */
    public static final String KEY_CHAIN_HEIGHT = "chainHeight";

    /**
     * Detail key: number of transactions currently in the mempool.
     */
    public static final String KEY_MEMPOOL_SIZE = "mempoolSize";

    /**
     * Detail key: number of currently connected peers.
     */
    public static final String KEY_PEER_COUNT = "peerCount";

    /**
     * Detail key: name of the active consensus engine.
     */
    public static final String KEY_ENGINE = "consensusEngine";

    /**
     * Detail key: ISO-8601 timestamp of the most recently added block.
     */
    public static final String KEY_LAST_BLOCK = "lastBlockTime";

    /**
     * Detail key: human-readable error reason when status is DOWN.
     */
    public static final String KEY_REASON = "reason";

    // ─── State ───────────────────────────────────────────────────────────────

    private final BlockchainNode node;

    // ─── Constructor ─────────────────────────────────────────────────────────

    /**
     * Creates a health indicator wrapping the given blockchain node.
     *
     * <p>Called by {@link BlockchainAutoConfiguration} — do not invoke directly.</p>
     *
     * @param node the node to report on (non-null)
     * @throws NullPointerException if {@code node} is null
     */
    public BlockchainHealthIndicator(BlockchainNode node) {
        this.node = Objects.requireNonNull(node, "node must not be null");
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Returns {@code true} when the node is running and {@link BlockchainNode#status()}
     * can be called without throwing.
     *
     * @return {@code true} if the node is UP
     */
    public boolean isUp() {
        try {
            node.status();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns a snapshot of the node's health details as an immutable map.
     *
     * <p>Always contains {@link #KEY_STATUS} ({@code "UP"} or {@code "DOWN"}).
     * When UP, also contains chain height, mempool size, peer count, consensus
     * engine name, and last block time. When DOWN, also contains
     * {@link #KEY_REASON} with a human-readable explanation.</p>
     *
     * @return non-null, non-empty detail map
     */
    public Map<String, Object> getDetails() {
        try {
            BlockchainNode.NodeStatus status = node.status();

            Map<String, Object> details = new HashMap<>();
            details.put(KEY_STATUS, "UP");
            details.put(KEY_CHAIN_HEIGHT, status.chainHeight());
            details.put(KEY_MEMPOOL_SIZE, status.mempoolSize());
            details.put(KEY_PEER_COUNT, status.peerCount());
            details.put(KEY_ENGINE, status.consensusEngine());

            Instant lastBlock = status.lastBlockTime();
            details.put(KEY_LAST_BLOCK, lastBlock != null ? lastBlock.toString() : "N/A");

            return Map.copyOf(details);

        } catch (IllegalStateException e) {
            return Map.of(
                KEY_STATUS, "DOWN",
                KEY_REASON, "BlockchainNode is not yet started",
                "error", e.getMessage() != null ? e.getMessage() : "unknown"
            );
        } catch (Exception e) {
            return Map.of(
                KEY_STATUS, "DOWN",
                KEY_REASON, "Unexpected error querying BlockchainNode",
                "error", e.getMessage() != null ? e.getMessage() : "unknown"
            );
        }
    }
}
