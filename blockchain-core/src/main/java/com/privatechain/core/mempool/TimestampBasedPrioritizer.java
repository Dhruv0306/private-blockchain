package com.privatechain.core.mempool;

import com.privatechain.core.model.Transaction;
import com.privatechain.core.spi.TransactionPrioritizer;

import java.io.Serial;
import java.io.Serializable;

/**
 * {@link TransactionPrioritizer} implementation that orders transactions
 * by their submission timestamp in ascending order (oldest first — FIFO).
 *
 * <p>This prioritizer implements naive FIFO ordering and is suitable for
 * testing or scenarios where all transactions are considered equally important
 * (FR-MEMPOOL-03).</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * TransactionPrioritizer prioritizer = new TimestampBasedPrioritizer();
 * TransactionMempool mempool = new TransactionMempool(prioritizer);
 * }</pre>
 *
 * @see TransactionMempool
 * @since 1.0.0
 */
public final class TimestampBasedPrioritizer implements TransactionPrioritizer, Serializable {

    /**
     * Serial version UID for serialization compatibility.
     */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Compares two transactions by their creation timestamp in ascending order.
     *
     * <p>Transactions with an earlier timestamp have higher priority
     * (return negative value in min-heap priority queue semantics).</p>
     *
     * @param t1 the first transaction (non-null)
     * @param t2 the second transaction (non-null)
     * @return negative if t1 is older, positive if t1 is newer,
     * zero if timestamps are equal
     */
    @Override
    public int compare(Transaction t1, Transaction t2) {
        // Older timestamps (lower values) have higher priority (come first)
        // compareTo returns: negative if t1 < t2, positive if t1 > t2
        return t1.getTimestamp().compareTo(t2.getTimestamp());
    }

    /**
     * Returns a human-readable description.
     *
     * @return string representation
     */
    @Override
    public String toString() {
        return "TimestampBasedPrioritizer{}";
    }
}

