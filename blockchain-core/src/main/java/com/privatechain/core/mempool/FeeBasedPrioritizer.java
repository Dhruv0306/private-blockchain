package com.privatechain.core.mempool;

import com.privatechain.core.model.Transaction;
import com.privatechain.core.spi.TransactionPrioritizer;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Orders transactions by fee in descending order (highest fee = highest priority).
 *
 * <p>The fee is derived from the {@link Transaction#getMetadata()} map using the key
 * {@value #FEE_METADATA_KEY}. Transactions that do not carry a fee entry are treated
 * as zero-fee and sorted after all fee-paying transactions (FR-MEMPOOL-03).</p>
 *
 * <h2>Fee extraction</h2>
 * <p>The fee value must be stored in {@code transaction.getMetadata()} under the key
 * {@code "fee"} as a {@link BigDecimal}, {@link java.math.BigInteger}, {@link Long},
 * {@link Integer}, or any {@link Number} subtype. String values are also accepted and
 * parsed via {@link BigDecimal#BigDecimal(String)}. Any other type falls back to
 * zero-fee ordering.</p>
 *
 * <h2>Ordering contract</h2>
 * <ul>
 *   <li>Higher fee → lower comparator return value (sorted to head of queue).</li>
 *   <li>Equal fees are broken by timestamp ascending (oldest first) for fairness.</li>
 *   <li>Equal fees and timestamps are broken by UUID for full stability.</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Include fee in transaction metadata before submitting
 * tx.getMetadata().put("fee", new BigDecimal("0.001"));
 *
 * TransactionMempool mempool = new TransactionMempool(new FeeBasedPrioritizer(), eventBus);
 * mempool.submit(tx);
 * }</pre>
 *
 * @see TimestampBasedPrioritizer
 * @see TransactionPrioritizer
 * @since 1.0.0
 */
public final class FeeBasedPrioritizer implements TransactionPrioritizer, Serializable {

    /**
     * Metadata key used to extract the transaction fee.
     */
    public static final String FEE_METADATA_KEY = "fee";
    @Serial
    private static final long serialVersionUID = 1L;
    /**
     * Fallback prioritizer for stable tiebreaking when fees are equal.
     */
    private static final TimestampBasedPrioritizer TIMESTAMP_TIEBREAKER =
        new TimestampBasedPrioritizer();

    /**
     * Extracts the fee from the transaction's metadata map.
     *
     * <p>Returns {@link BigDecimal#ZERO} if no fee entry is present or if the
     * stored value cannot be interpreted as a numeric fee.</p>
     *
     * @param tx the transaction whose fee to extract (non-null)
     * @return the fee as a {@link BigDecimal}; never {@code null}
     */
    private static BigDecimal extractFee(Transaction tx) {
        if (tx.getMetadata() == null) {
            return BigDecimal.ZERO;
        }

        Object feeObj = tx.getMetadata().get(FEE_METADATA_KEY);
        if (feeObj == null) {
            return BigDecimal.ZERO;
        }

        // Accept BigDecimal directly (most common)
        if (feeObj instanceof BigDecimal bd) {
            return bd;
        }

        // Accept any Number subtype (Long, Integer, Double, etc.)
        if (feeObj instanceof Number num) {
            try {
                return new BigDecimal(num.toString());
            } catch (NumberFormatException ex) {
                return BigDecimal.ZERO;
            }
        }

        // Accept String representations
        if (feeObj instanceof String s) {
            try {
                return new BigDecimal(s.trim());
            } catch (NumberFormatException ex) {
                return BigDecimal.ZERO;
            }
        }

        // Unrecognised type — zero fee
        return BigDecimal.ZERO;
    }

    /**
     * Compares two transactions by fee in descending order.
     *
     * <p>A negative return value means {@code t1} should be selected before {@code t2}
     * (i.e., {@code t1} has a higher or equal fee).</p>
     *
     * @param t1 the first transaction (non-null)
     * @param t2 the second transaction (non-null)
     * @return negative if {@code t1} has higher priority, positive if {@code t2} does
     */
    @Override
    public int compare(Transaction t1, Transaction t2) {
        BigDecimal fee1 = extractFee(t1);
        BigDecimal fee2 = extractFee(t2);

        // Descending fee order: higher fee → comes first → negative comparator result
        int cmp = fee2.compareTo(fee1);
        if (cmp != 0) {
            return cmp;
        }

        // Stable tiebreaker: oldest timestamp first (FIFO within same fee tier)
        return TIMESTAMP_TIEBREAKER.compare(t1, t2);
    }

    /**
     * Returns a human-readable name for this prioritizer.
     *
     * @return {@code "FeeBasedPrioritizer[order=fee-desc]"}
     */
    @Override
    public String toString() {
        return "FeeBasedPrioritizer[order=fee-desc]";
    }
}

