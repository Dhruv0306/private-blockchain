package com.privatechain.network.sync;

import com.privatechain.core.model.Block;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Resolves chain forks by selecting the canonical chain according to the
 * longest-chain (greatest cumulative difficulty) rule (FR-NET-05).
 *
 * <p>When {@link SyncManager} discovers that a peer has a longer or heavier chain than
 * the local chain, it uses {@code ForkResolver} to decide whether to switch to the
 * peer's chain. Two chain comparison strategies are supported:</p>
 *
 * <ul>
 *   <li><strong>Chain length</strong> — the chain with more blocks wins (suitable for
 *       Proof-of-Work chains where each block represents equal work).</li>
 *   <li><strong>Cumulative difficulty</strong> — the chain whose blocks' {@code bits}
 *       values sum to the larger total wins (suitable for chains with variable difficulty
 *       e.g., after {@code DifficultyAdjuster} runs). This is the canonical Bitcoin rule.</li>
 * </ul>
 *
 * <p>For chains of equal weight, the local chain is retained (no switch).
 * This "prefer local" tie-break ensures that the network stabilizes rather than
 * oscillating between equally valid forks.</p>
 *
 * <h2>Thread safety</h2>
 * <p>All methods are stateless and safe for concurrent use.</p>
 *
 * @see SyncManager
 * @since 1.0.0
 */
public final class ForkResolver {

    private static final Logger LOGGER = Logger.getLogger(ForkResolver.class.getName());

    // ─── Constructor ──────────────────────────────────────────────────────────

    /**
     * Constructs a {@code ForkResolver}. No external dependencies required.
     */
    public ForkResolver() {
    }

    // ─── Resolution ───────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the candidate chain should replace the local chain.
     *
     * <p>The comparison uses cumulative difficulty (sum of {@code bits} fields)
     * as the primary metric, falling back to chain height if difficulties are equal.
     * If both metrics are equal, the local chain is preferred (no switch).</p>
     *
     * @param localChain     the local node's current canonical chain (non-null, non-empty)
     * @param candidateChain the peer's chain being evaluated (non-null, non-empty)
     * @return {@code true} if the candidate chain is strictly better and should be adopted
     * @throws NullPointerException     if either chain is null
     * @throws IllegalArgumentException if either chain is empty
     */
    public boolean shouldSwitchTo(List<Block> localChain, List<Block> candidateChain) {
        Objects.requireNonNull(localChain, "localChain must not be null");
        Objects.requireNonNull(candidateChain, "candidateChain must not be null");
        if (localChain.isEmpty()) {
            throw new IllegalArgumentException("localChain must not be empty");
        }
        if (candidateChain.isEmpty()) {
            throw new IllegalArgumentException("candidateChain must not be empty");
        }

        long localDifficulty = cumulativeDifficulty(localChain);
        long candidateDifficulty = cumulativeDifficulty(candidateChain);

        if (candidateDifficulty > localDifficulty) {
            LOGGER.info(() -> "Fork resolution: switching to candidate chain "
                + "(candidateDifficulty=" + candidateDifficulty
                + " > localDifficulty=" + localDifficulty
                + ", candidateHeight=" + candidateChain.size()
                + ", localHeight=" + localChain.size() + ")");
            return true;
        }

        if (candidateDifficulty == localDifficulty && candidateChain.size() > localChain.size()) {
            LOGGER.info(() -> "Fork resolution: switching to longer candidate chain "
                + "(equal difficulty, candidateHeight=" + candidateChain.size()
                + " > localHeight=" + localChain.size() + ")");
            return true;
        }

        LOGGER.fine(() -> "Fork resolution: retaining local chain "
            + "(localDifficulty=" + localDifficulty
            + ", candidateDifficulty=" + candidateDifficulty + ")");
        return false;
    }

    /**
     * Selects the heavier chain from two competing chains.
     *
     * <p>Equivalent to calling {@link #shouldSwitchTo(List, List)} and returning
     * the winner. When both chains are equal, the first argument (local chain) is
     * returned.</p>
     *
     * @param chainA the first chain (typically the local chain) (non-null, non-empty)
     * @param chainB the second chain (typically a peer's chain) (non-null, non-empty)
     * @return the heavier chain, or {@code chainA} on tie
     */
    public List<Block> resolveCanonical(List<Block> chainA, List<Block> chainB) {
        return shouldSwitchTo(chainA, chainB) ? chainB : chainA;
    }

    // ─── Metrics ──────────────────────────────────────────────────────────────

    /**
     * Computes the cumulative difficulty of a chain as the sum of all block {@code bits} values.
     *
     * <p>In PoW chains, {@code bits} encodes the compact target difficulty. Summing these
     * values provides the chain's total work as a proxy for cumulative hash difficulty
     * (the Bitcoin-compatible metric for fork selection, FR-NET-05).</p>
     *
     * @param chain the chain to measure (non-null, non-empty)
     * @return cumulative difficulty (&ge; 0)
     */
    public long cumulativeDifficulty(List<Block> chain) {
        Objects.requireNonNull(chain, "chain must not be null");
        return chain.stream()
            .mapToLong(b -> b.getHeader().bits())
            .sum();
    }

    /**
     * Returns the block with the greatest {@code bits} value in the chain.
     *
     * <p>Useful for identifying the "heaviest" individual block, e.g., when logging
     * difficulty anomalies or debugging consensus issues.</p>
     *
     * @param chain the chain to search (non-null, non-empty)
     * @return the block with the maximum {@code bits} value
     * @throws IllegalArgumentException if chain is empty
     */
    public Block heaviestBlock(List<Block> chain) {
        Objects.requireNonNull(chain, "chain must not be null");
        return chain.stream()
            .max(Comparator.comparingInt(b -> b.getHeader().bits()))
            .orElseThrow(() -> new IllegalArgumentException("chain must not be empty"));
    }
}
