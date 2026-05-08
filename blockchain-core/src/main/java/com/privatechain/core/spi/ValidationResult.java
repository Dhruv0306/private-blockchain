package com.privatechain.core.spi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable result returned by {@link TransactionValidator#validate}.
 *
 * <p>A result is either successful or failed. On failure, it carries a non-empty
 * list of human-readable error messages and a {@link ValidationStatus} classifying
 * the failure category (FR-TX-02).</p>
 *
 * <p>Factory methods {@link #success()}, {@link #failure(ValidationStatus, String)},
 * and {@link #failure(ValidationStatus, List)} cover the common construction
 * patterns.</p>
 *
 * <pre>{@code
 * // In a validator implementation:
 * if (!signatureOk) {
 *     return ValidationResult.failure(
 *         ValidationStatus.INVALID_SIGNATURE,
 *         "Signature does not match sender public key");
 * }
 * return ValidationResult.success();
 * }</pre>
 *
 * @see TransactionValidator
 * @see ValidationStatus
 * @since 1.0.0
 */
public final class ValidationResult {

    // ─── Singleton for the happy path ─────────────────────────────────────────
    private static final ValidationResult SUCCESS =
        new ValidationResult(true, ValidationStatus.VALID, Collections.emptyList());

    // ─── Fields ───────────────────────────────────────────────────────────────

    private final boolean success;
    private final ValidationStatus status;
    private final List<String> errors;

    // ─── Private constructor ───────────────────────────────────────────────────

    /**
     * Private full constructor.
     *
     * @param success whether validation passed
     * @param status  status enum
     * @param errors  list of error messages (empty on success)
     */
    private ValidationResult(boolean success, ValidationStatus status, List<String> errors) {
        this.success = success;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.errors = Collections.unmodifiableList(
            new ArrayList<>(Objects.requireNonNull(errors, "errors must not be null")));
    }

    // ─── Factory methods ──────────────────────────────────────────────────────

    /**
     * Returns the canonical singleton representing a successful validation.
     *
     * @return a {@code ValidationResult} with {@code success=true} and {@link ValidationStatus#VALID}
     */
    public static ValidationResult success() {
        return SUCCESS;
    }

    /**
     * Creates a failed result with a single error message.
     *
     * @param status  classification of the failure (non-null, must not be {@link ValidationStatus#VALID})
     * @param message human-readable error description (non-null, non-blank)
     * @return a failed {@code ValidationResult}
     * @throws NullPointerException     if status or message is null
     * @throws IllegalArgumentException if status is {@link ValidationStatus#VALID} or message is blank
     */
    public static ValidationResult failure(ValidationStatus status, String message) {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(message, "message must not be null");
        if (status == ValidationStatus.VALID) {
            throw new IllegalArgumentException("Use ValidationResult.success() for VALID status");
        }
        if (message.isBlank()) {
            throw new IllegalArgumentException("error message must not be blank");
        }
        return new ValidationResult(false, status, List.of(message));
    }

    /**
     * Creates a failed result with multiple error messages.
     *
     * @param status classification of the failure (non-null, must not be {@link ValidationStatus#VALID})
     * @param errors list of human-readable error descriptions (non-null, non-empty)
     * @return a failed {@code ValidationResult}
     * @throws NullPointerException     if status or errors is null
     * @throws IllegalArgumentException if status is {@link ValidationStatus#VALID} or errors is empty
     */
    public static ValidationResult failure(ValidationStatus status, List<String> errors) {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(errors, "errors must not be null");
        if (status == ValidationStatus.VALID) {
            throw new IllegalArgumentException("Use ValidationResult.success() for VALID status");
        }
        if (errors.isEmpty()) {
            throw new IllegalArgumentException("errors list must not be empty for a failed result");
        }
        return new ValidationResult(false, status, errors);
    }

    // ─── Accessors ────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the transaction passed all validation checks.
     *
     * @return {@code true} on success
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Returns {@code true} if the transaction failed one or more validation checks.
     *
     * @return {@code true} on failure
     */
    public boolean isFailure() {
        return !success;
    }

    /**
     * Returns the status classification of the validation outcome.
     *
     * @return {@link ValidationStatus#VALID} on success, or a failure enum value
     */
    public ValidationStatus getStatus() {
        return status;
    }

    /**
     * Returns the list of human-readable error descriptions.
     *
     * @return unmodifiable list; empty on success, non-empty on failure
     */
    public List<String> getErrors() {
        return errors;
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
        if (!(obj instanceof ValidationResult other)) {
            return false;
        }
        return success == other.success
            && status == other.status
            && Objects.equals(errors, other.errors);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(success, status, errors);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return success
            ? "ValidationResult{VALID}"
            : "ValidationResult{status=" + status + ", errors=" + errors + '}';
    }

    // ─── Nested enum ──────────────────────────────────────────────────────────

    /**
     * Classification of a transaction validation outcome.
     *
     * <p>Consumers can switch on this enum to produce domain-appropriate error responses
     * (e.g., HTTP 400 Bad Request vs 402 Payment Required).</p>
     *
     * @since 1.0.0
     */
    public enum ValidationStatus {

        /**
         * Transaction passed all validation checks.
         */
        VALID,

        /**
         * The ECDSA signature does not match the sender's public key.
         */
        INVALID_SIGNATURE,

        /**
         * The sender does not have sufficient funds to cover the transaction amount.
         */
        INSUFFICIENT_FUNDS,

        /**
         * A transaction with the same ID already exists in the chain or mempool.
         */
        DUPLICATE,

        /**
         * A custom validator rejected the transaction for a business-logic reason.
         */
        CUSTOM_REJECTION
    }
}
