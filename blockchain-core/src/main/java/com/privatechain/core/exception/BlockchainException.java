package com.privatechain.core.exception;

import java.io.Serial;

/**
 * Root unchecked exception for all errors thrown by the private-blockchain library.
 *
 * <p>All library-specific exceptions extend this class so that consumers can write
 * a single {@code catch (BlockchainException e)} to handle any library error
 * (NFR-UX-03). Using an unchecked base means callers are never forced to declare
 * checked exceptions in their APIs, keeping consumer code clean.</p>
 *
 * <p><strong>Do not instantiate this class directly.</strong> Use one of the
 * specific subtypes:</p>
 * <ul>
 *   <li>{@link BlockValidationException} — block failed consensus or integrity check</li>
 *   <li>{@link ConsensusException} — unrecoverable error in the consensus engine</li>
 *   <li>{@link TransactionValidationException} — transaction failed validation</li>
 * </ul>
 *
 * @since 1.0.0
 */
public abstract class BlockchainException extends RuntimeException {

    /**
     * Serial version UID for stable serialization.
     */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a {@code BlockchainException} with a detail message.
     *
     * @param message human-readable description of the error
     */
    protected BlockchainException(String message) {
        super(message);
    }

    /**
     * Constructs a {@code BlockchainException} with a detail message and cause.
     *
     * @param message human-readable description of the error
     * @param cause   the underlying exception that triggered this one
     */
    protected BlockchainException(String message, Throwable cause) {
        super(message, cause);
    }
}
