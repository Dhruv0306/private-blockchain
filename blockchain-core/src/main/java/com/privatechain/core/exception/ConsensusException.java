package com.privatechain.core.exception;

import java.io.Serial;

/**
 * Thrown when a {@link com.privatechain.core.spi.ConsensusEngine} encounters an
 * unrecoverable error during block production or validation.
 *
 * <p>Examples:</p>
 * <ul>
 *   <li>PBFT cannot reach the required quorum (e.g., too many Byzantine nodes)</li>
 *   <li>PoA signer key is unavailable or corrupt</li>
 *   <li>Mining thread is interrupted mid-nonce-search</li>
 * </ul>
 *
 * @since 1.0.0
 */
public final class ConsensusException extends BlockchainException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a {@code ConsensusException} with a detail message.
     *
     * @param message human-readable description of the consensus failure
     */
    public ConsensusException(String message) {
        super(message);
    }

    /**
     * Constructs a {@code ConsensusException} with a detail message and cause.
     *
     * @param message human-readable description of the consensus failure
     * @param cause   the underlying exception (e.g., network timeout)
     */
    public ConsensusException(String message, Throwable cause) {
        super(message, cause);
    }
}
