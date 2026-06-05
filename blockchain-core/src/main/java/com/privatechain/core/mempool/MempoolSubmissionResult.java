package com.privatechain.core.mempool;

import java.util.List;
import java.util.Objects;

/**
 * Immutable result of a {@link TransactionMempool#submitWithValidation} call.
 *
 * <p>Carries a boolean acceptance flag and, on rejection, the list of error messages
 * produced by the configured {@link com.privatechain.core.spi.TransactionValidator}
 * chain.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * MempoolSubmissionResult result = mempool.submitWithValidation(tx, blockchain);
 * if (!result.isAccepted()) {
 *     log.warn("Transaction rejected: {}", result.getRejectionReasons());
 * }
 * }</pre>
 *
 * @see TransactionMempool
 * @since 1.0.0
 */
public final class MempoolSubmissionResult {

    // ─── Factory methods ──────────────────────────────────────────────────────

    /**
     * {@code true} when the transaction was accepted into the pool.
     */
    private final boolean accepted;
    /**
     * Human-readable rejection messages produced by the validator chain.
     * Empty when {@link #accepted} is {@code true}.
     */
    private final List<String> rejectionReasons;

    // ─── State ────────────────────────────────────────────────────────────────

    /**
     * Private — use factory methods {@link #accepted()} or {@link #rejected(List)}.
     *
     * @param accepted         {@code true} if the transaction was accepted
     * @param rejectionReasons list of rejection reasons; empty on acceptance
     */
    private MempoolSubmissionResult(boolean accepted, List<String> rejectionReasons) {
        this.accepted = accepted;
        this.rejectionReasons = List.copyOf(rejectionReasons);
    }

    /**
     * Creates a result indicating the transaction was accepted into the mempool.
     *
     * @return a successful {@code MempoolSubmissionResult}
     */
    public static MempoolSubmissionResult accepted() {
        return new MempoolSubmissionResult(true, List.of());
    }

    // ─── Constructor ──────────────────────────────────────────────────────────

    /**
     * Creates a result indicating the transaction was rejected.
     *
     * @param reasons non-null list of rejection messages (maybe empty)
     * @return a failed {@code MempoolSubmissionResult}
     * @throws NullPointerException if reasons is null
     */
    public static MempoolSubmissionResult rejected(List<String> reasons) {
        Objects.requireNonNull(reasons, "reasons must not be null");
        return new MempoolSubmissionResult(false, List.copyOf(reasons));
    }

    // ─── Accessors ────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the transaction was accepted into the mempool.
     *
     * @return {@code true} on acceptance
     */
    public boolean isAccepted() {
        return accepted;
    }

    /**
     * Returns {@code true} if the transaction was rejected.
     *
     * <p>Convenience inverse of {@link #isAccepted()}.</p>
     *
     * @return {@code true} on rejection
     */
    public boolean isRejected() {
        return !accepted;
    }

    /**
     * Returns the list of human-readable rejection reasons.
     *
     * <p>Returns an empty list when the transaction was accepted.</p>
     *
     * @return non-null, unmodifiable list of rejection messages
     */
    public List<String> getRejectionReasons() {
        // Return a defensive unmodifiable view to avoid exposing internal state.
        return List.copyOf(rejectionReasons);
    }

    // ─── Object overrides ─────────────────────────────────────────────────────

    /**
     * Returns a human-readable string representation of this result.
     *
     * @return a string like {@code "MempoolSubmissionResult[accepted=true]"} or
     * {@code "MempoolSubmissionResult[rejected, reasons=[...]]"}
     */
    @Override
    public String toString() {
        if (accepted) {
            return "MempoolSubmissionResult[accepted=true]";
        }
        return "MempoolSubmissionResult[rejected, reasons=" + rejectionReasons + "]";
    }

    /**
     * Two results are equal if they have the same acceptance status and rejection reasons.
     *
     * @param obj the object to compare with
     * @return {@code true} if equal
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof MempoolSubmissionResult other)) return false;
        return accepted == other.accepted
            && Objects.equals(rejectionReasons, other.rejectionReasons);
    }

    /**
     * Returns a hash code consistent with {@link #equals}.
     *
     * @return hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(accepted, rejectionReasons);
    }
}
