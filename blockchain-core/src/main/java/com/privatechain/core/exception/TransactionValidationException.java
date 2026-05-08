package com.privatechain.core.exception;

import com.privatechain.core.model.Transaction;
import com.privatechain.core.spi.ValidationResult;

import java.io.Serial;

/**
 * Thrown when a {@link Transaction} fails one or more
 * {@link com.privatechain.core.spi.TransactionValidator} checks before entering
 * the mempool or being included in a block.
 *
 * <p>The exception carries the full {@link ValidationResult} so that callers can
 * inspect the specific failure reason and the list of error messages without
 * parsing the exception message string.</p>
 *
 * @see com.privatechain.core.spi.TransactionValidator
 * @see ValidationResult
 * @since 1.0.0
 */
public final class TransactionValidationException extends BlockchainException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The full validation result, including status and all error messages.
     */
    private final transient ValidationResult validationResult;

    /**
     * The transaction that triggered the exception. May be null if unavailable.
     */
    private final transient Transaction transaction;

    /**
     * Constructs a {@code TransactionValidationException}.
     *
     * @param message          human-readable summary of the validation failure
     * @param validationResult the full result from the validator (non-null)
     * @param transaction      the rejected transaction (maybe null)
     */
    public TransactionValidationException(
        String message,
        ValidationResult validationResult,
        Transaction transaction) {
        super(message);
        this.validationResult = validationResult;
        this.transaction = transaction;
    }

    /**
     * Returns the full {@link ValidationResult} that caused this exception.
     *
     * @return the validation result (non-null)
     */
    public ValidationResult getValidationResult() {
        return validationResult;
    }

    /**
     * Returns the transaction that failed validation.
     *
     * @return the rejected transaction, or {@code null} if not available
     */
    public Transaction getTransaction() {
        return transaction;
    }
}
