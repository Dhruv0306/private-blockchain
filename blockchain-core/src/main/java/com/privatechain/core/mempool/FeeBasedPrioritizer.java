package com.privatechain.core.mempool;

import com.privatechain.core.model.Transaction;
import com.privatechain.core.spi.TransactionPrioritizer;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * {@link TransactionPrioritizer} implementation that orders transactions
 * by their fee-per-byte in descending order (highest fee first).
 *
 * <p>The fee is derived from {@code transaction.getAmount()}, and the byte size
 * is estimated from {@code transaction.toSignableBytes().length}. This prioritizer
 * is suitable for Proof-of-Work or similar fee-based consensus systems
 * (FR-MEMPOOL-03).</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * TransactionPrioritizer prioritizer = new FeeBasedPrioritizer();
 * TransactionMempool mempool = new TransactionMempool(prioritizer);
 * }</pre>
 *
 * @see TransactionMempool
 * @since 1.0.0
 */
public final class FeeBasedPrioritizer implements TransactionPrioritizer, Serializable {

    /**
     * Serial version UID for serialization compatibility.
     */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Compares two transactions by fee-per-byte in descending order.
     *
     * <p>Transactions with higher fee-per-byte are ordered first
     * (return negative value in min-heap priority queue semantics).</p>
     *
     * @param t1 the first transaction (non-null)
     * @param t2 the second transaction (non-null)
     * @return negative if t1 has higher fee-per-byte, positive if t2 does,
     * zero if equal
     */
    @Override
    public int compare(Transaction t1, Transaction t2) {
        // Extract and calculate fees
        BigDecimal fee1 = t1.getAmount();
        BigDecimal fee2 = t2.getAmount();

        // Estimate byte sizes
        int size1 = t1.toSignableBytes().length;
        int size2 = t2.toSignableBytes().length;

        // Calculate fee-per-byte
        // fee1/size1 compared to fee2/size2
        // To avoid floating-point: fee1*size2 compared to fee2*size1
        // We invert the result because we want higher fee first
        BigDecimal feePerByte1 = fee1.multiply(BigDecimal.valueOf(size2));
        BigDecimal feePerByte2 = fee2.multiply(BigDecimal.valueOf(size1));

        // Negative means t1 is higher priority (comes first)
        return feePerByte2.compareTo(feePerByte1);
    }

    /**
     * Returns a human-readable description.
     *
     * @return string representation
     */
    @Override
    public String toString() {
        return "FeeBasedPrioritizer{}";
    }
}

