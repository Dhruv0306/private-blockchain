package com.privatechain.core.exception;

import com.privatechain.core.model.Block;

import java.io.Serial;

/**
 * Thrown when a {@link Block} fails validation during {@code Blockchain.addBlock()}.
 *
 * <p>Possible causes include:</p>
 * <ul>
 *   <li>The block's hash does not satisfy the consensus difficulty target</li>
 *   <li>{@code block.previousHash} does not match the current chain tip's hash</li>
 *   <li>The block's Merkle root does not match the computed root of its transactions</li>
 *   <li>The block's stored hash does not match a fresh computation (corruption)</li>
 * </ul>
 *
 * @see com.privatechain.core.spi.ConsensusEngine#validateBlock
 * @since 1.0.0
 */
public final class BlockValidationException extends BlockchainException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The invalid block, kept for diagnostic purposes. May be null if unavailable.
     */
    private final transient Block block;

    /**
     * Constructs an exception with a detail message and the offending block.
     *
     * @param message human-readable description of why the block was rejected
     * @param block   the block that failed validation (maybe null)
     */
    public BlockValidationException(String message, Block block) {
        super(message);
        this.block = block;
    }

    /**
     * Constructs an exception with a detail message, cause, and the offending block.
     *
     * @param message human-readable description of why the block was rejected
     * @param cause   the underlying exception
     * @param block   the block that failed validation (maybe null)
     */
    public BlockValidationException(String message, Throwable cause, Block block) {
        super(message, cause);
        this.block = block;
    }

    /**
     * Returns the block that triggered this exception.
     *
     * @return the invalid block, or {@code null} if not available
     */
    public Block getBlock() {
        return block;
    }
}
