package com.privatechain.core.mempool;

import com.privatechain.core.model.Transaction;
import com.privatechain.core.spi.TransactionPrioritizer;

import java.io.Serial;
import java.io.Serializable;

/**
 * Orders transactions by submission timestamp in ascending order (oldest first / FIFO).
 *
 * <p>This prioritizer is the default used when no explicit prioritizer is configured
 * via {@link com.privatechain.core.builder.BlockchainConfig.Builder#transactionPrioritizer(TransactionPrioritizer)}.
 * It favors transactions that have been waiting longest, which provides a fair,
 * predictable ordering suitable for development and testing (FR-MEMPOOL-03).</p>
 *
 * <h2>Ordering contract</h2>
 * <ul>
 *   <li>A transaction with an earlier timestamp sorts <em>before</em> one with a later
 *       timestamp (ascending / the lowest timestamp = highest priority in the queue).</li>
 *   <li>When two transactions share the same timestamp, their UUID natural order is
 *       used as a stable tiebreaker to ensure deterministic sorting.</li>
 *   <li>{@code null} timestamps are treated as {@link Long#MAX_VALUE} (lowest priority),
 *       which guards against NPEs from partially-constructed objects.</li>
 * </ul>
 *
 * @see FeeBasedPrioritizer
 * @see TransactionPrioritizer
 * @since 1.0.0
 */
public final class TimestampBasedPrioritizer implements TransactionPrioritizer, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Compares two transactions by their submission timestamps.
     *
     * <p>Returns a negative integer if {@code t1} should be selected before {@code t2}
     * (i.e., {@code t1} has an earlier timestamp).</p>
     * <p>Returns a positive integer if {@code t1} should be selected after {@code t2}
     * (i.e., {@code t1} has a newer timestamp).</p>
     * <p>If both transections has a same timestamp then we compare their transections id.</p>
     * <p>Returns a negative integer if {@code t1.getId()} is less than {@code t2.getId()}</p>
     * <p>Returns a positive integer if {@code t1.getId()} is greater than {@code t2.getId()}</p>
     *
     * @param t1 the first transaction (non-null)
     * @param t2 the second transaction (non-null)
     * @return negative if {@code t1} sorts first, positive if {@code t2} sorts first
     */
    @Override
    public int compare(Transaction t1, Transaction t2) {
        // Extract epoch millis — null-safe via fallback to MAX_VALUE
        long ts1 = (t1.getTimestamp() != null) ? t1.getTimestamp().toEpochMilli() : Long.MAX_VALUE;
        long ts2 = (t2.getTimestamp() != null) ? t2.getTimestamp().toEpochMilli() : Long.MAX_VALUE;

        int cmp = Long.compare(ts1, ts2);
        if (cmp != 0) {
            return cmp;
        }

        // Stable tiebreaker: UUID natural ordering (lexicographic on string representation)
        if (t1.getId() != null && t2.getId() != null) {
            return t1.getId().compareTo(t2.getId());
        }
        return 0;
    }

    /**
     * Returns a human-readable name for this prioritizer.
     *
     * @return {@code "timestamp-asc"}
     */
    @Override
    public String toString() {
        return "TimestampBasedPrioritizer[order=timestamp-asc]";
    }
}

