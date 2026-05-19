package com.privatechain.core.mempool;

import com.privatechain.core.spi.ValidationResult;

import java.util.Objects;

/**
 * Result of a transaction submission attempt to the {@link TransactionMempool}.
 *
 * <p>A submission can succeed, fail validation, or represent a duplicate transaction.
 * Callers can inspect the result to determine whether to retry or take alternative action.</p>
 *
 * <pre>{@code
 * MempoolSubmissionResult result = mempool.submitWithValidation(tx, blockchain);
 * if (result.isAccepted()) {
 *     // Transaction was added to the pool
 * } else if (result.isValidationFailed()) {
 *     // Transaction validation failed; details in result.getValidationResult()
 *     System.err.println("Validation error: " + result.getValidationResult().getErrors());
 * } else if (result.isDuplicate()) {
 *     // Transaction with this ID already exists in the pool
 * }
 * }</pre>
 *
 * @see TransactionMempool#submitWithValidation
 * @since 1.0.0
 */
public final class MempoolSubmissionResult {

    // ─── Status enumeration ───────────────────────────────────────────────────

    private static final MempoolSubmissionResult ACCEPTED_INSTANCE =
        new MempoolSubmissionResult(Status.ACCEPTED, null);

    // ─── Singleton instances ──────────────────────────────────────────────────
    private static final MempoolSubmissionResult DUPLICATE_INSTANCE =
        new MempoolSubmissionResult(Status.DUPLICATE, null);
    private final Status status;

    // ─── Fields ───────────────────────────────────────────────────────────────
    private final ValidationResult validationResult; // Non-null only when status == VALIDATION_FAILED
    /**
     * Private constructor.
     *
     * @param status           the outcome status (non-null)
     * @param validationResult validation result (non-null iff status == VALIDATION_FAILED)
     */
    private MempoolSubmissionResult(Status status, ValidationResult validationResult) {
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.validationResult = validationResult;
    }

    // ─── Private constructor ──────────────────────────────────────────────────

    /**
     * Creates a successful submission result.
     *
     * <p>Uses a reusable singleton instance for this common case.</p>
     *
     * @return a result indicating the transaction was accepted
     */
    public static MempoolSubmissionResult accepted() {
        return ACCEPTED_INSTANCE;
    }

    // ─── Factory methods ───────────────────────────────────────────────────────

    /**
     * Creates a validation-failed result.
     *
     * @param validationResult the validation failure details (non-null)
     * @return a result indicating validation failure
     * @throws NullPointerException     if validationResult is null
     * @throws IllegalArgumentException if validationResult indicates success
     */
    public static MempoolSubmissionResult rejected(ValidationResult validationResult) {
        Objects.requireNonNull(validationResult, "validationResult must not be null");
        if (!validationResult.isFailure()) {
            throw new IllegalArgumentException(
                "validationResult must indicate failure for rejected() factory");
        }
        return new MempoolSubmissionResult(Status.VALIDATION_FAILED, validationResult);
    }

    /**
     * Creates a duplicate transaction result.
     *
     * <p>Uses a reusable singleton instance for this common case.</p>
     *
     * @return a result indicating the transaction already exists in the pool
     */
    public static MempoolSubmissionResult duplicate() {
        return DUPLICATE_INSTANCE;
    }

    /**
     * Returns the outcome status of the submission attempt.
     *
     * @return the {@link Status}
     */
    public Status getStatus() {
        return status;
    }

    // ─── Accessors ────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the transaction was accepted.
     *
     * @return {@code true} iff status is {@link Status#ACCEPTED}
     */
    public boolean isAccepted() {
        return status == Status.ACCEPTED;
    }

    /**
     * Returns {@code true} if validation failed.
     *
     * @return {@code true} iff status is {@link Status#VALIDATION_FAILED}
     */
    public boolean isValidationFailed() {
        return status == Status.VALIDATION_FAILED;
    }

    /**
     * Returns {@code true} if the transaction is a duplicate.
     *
     * @return {@code true} iff status is {@link Status#DUPLICATE}
     */
    public boolean isDuplicate() {
        return status == Status.DUPLICATE;
    }

    /**
     * Returns the validation failure details, if available.
     *
     * @return the {@link ValidationResult} if status is {@link Status#VALIDATION_FAILED},
     * otherwise {@code null}
     */
    public ValidationResult getValidationResult() {
        return validationResult;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return switch (status) {
            case ACCEPTED -> "MempoolSubmissionResult{ACCEPTED}";
            case DUPLICATE -> "MempoolSubmissionResult{DUPLICATE}";
            case VALIDATION_FAILED -> "MempoolSubmissionResult{VALIDATION_FAILED, " + validationResult + '}';
        };
    }

    // ─── Object overrides ─────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof MempoolSubmissionResult other)) {
            return false;
        }
        return status == other.status
            && Objects.equals(validationResult, other.validationResult);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(status, validationResult);
    }

    /**
     * Outcome of a mempool submission attempt.
     *
     * @since 1.0.0
     */
    public enum Status {
        /**
         * Transaction was accepted and added to the pool.
         */
        ACCEPTED,
        /**
         * Transaction failed validation and was rejected.
         */
        VALIDATION_FAILED,
        /**
         * Transaction with the same ID already exists in the pool.
         */
        DUPLICATE
    }
}

