package com.privatechain.core.spi;

import com.privatechain.core.model.Transaction;

import java.util.Comparator;

/**
 * Service Provider Interface (SPI) for ordering transactions in the mempool.
 *
 * <p>Implement this interface to control which transactions are selected first
 * when a miner assembles the next block. The built-in implementations are:</p>
 * <ul>
 *   <li>{@code FeeBasedPrioritizer} — highest fee-per-byte first (FR-MEMPOOL-03)</li>
 *   <li>{@code TimestampBasedPrioritizer} — oldest submitted first (FIFO)</li>
 * </ul>
 *
 * <p>The interface extends {@link Comparator}{@code <Transaction>} so that
 * implementations can be used directly with Java's standard collection utilities
 * (e.g., {@link java.util.PriorityQueue}).</p>
 *
 * <p>A positive return value from {@link #compare} means the first transaction
 * has <em>lower</em> priority than the second (consistent with min-heap ordering
 * in {@link java.util.PriorityQueue}, where the head is the least element).</p>
 *
 * <pre>{@code
 * // Example: always prefer transactions from a VIP address
 * public class VipPrioritizer implements TransactionPrioritizer {
 *     \@Override
 *     public int compare(Transaction a, Transaction b) {
 *         boolean aVip = VIP_SET.contains(a.getSenderAddress());
 *         boolean bVip = VIP_SET.contains(b.getSenderAddress());
 *         if (aVip && !bVip) return -1; // a has higher priority
 *         if (!aVip && bVip) return  1;
 *         return a.getTimestamp().compareTo(b.getTimestamp());
 *     }
 * }
 * }</pre>
 *
 * @see com.privatechain.core.builder.BlockchainConfig
 * @since 1.0.0
 */
public interface TransactionPrioritizer extends Comparator<Transaction> {

    /**
     * Compares two transactions to determine their relative priority.
     *
     * <p>Implementations must be consistent, transitive, and free of side effects.
     * A return value of {@code -1} (or any negative number) means {@code t1} should
     * be selected before {@code t2}; {@code +1} (or positive) means {@code t2} first;
     * {@code 0} means equal priority.</p>
     *
     * @param t1 the first transaction (non-null)
     * @param t2 the second transaction (non-null)
     * @return negative if {@code t1} has higher priority, positive if {@code t2} does,
     * zero if equal priority
     */
    @Override
    int compare(Transaction t1, Transaction t2);
}
