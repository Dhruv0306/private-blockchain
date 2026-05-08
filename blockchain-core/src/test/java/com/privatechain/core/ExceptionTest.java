package com.privatechain.core;

import com.privatechain.core.exception.BlockValidationException;
import com.privatechain.core.exception.BlockchainException;
import com.privatechain.core.exception.ConsensusException;
import com.privatechain.core.exception.TransactionValidationException;
import com.privatechain.core.model.Block;
import com.privatechain.core.model.BlockHeader;
import com.privatechain.core.model.Transaction;
import com.privatechain.core.spi.ValidationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the exception hierarchy in {@code com.privatechain.core.exception}.
 * Covers constructors, message propagation, cause chaining, and domain-specific getters.
 */
@DisplayName("Exception hierarchy")
class ExceptionTest {

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Block sampleBlock() {
        BlockHeader header = BlockHeader.builder()
            .nonce(0L).merkleRoot("a".repeat(64)).build();
        return Block.builder()
            .index(0)
            .previousHash(Block.GENESIS_PREVIOUS_HASH)
            .transactions(List.of())
            .header(header)
            .build();
    }

    private Transaction sampleTransaction() {
        return new Transaction(
            UUID.randomUUID(), "s", "r", BigDecimal.ONE, Instant.now(), null) {
        };
    }

    // ─── BlockchainException (abstract root) ──────────────────────────────────

    @Test
    @DisplayName("all exceptions extend BlockchainException (unchecked root)")
    void allExceptionsExtendRoot() {
        assertInstanceOf(BlockchainException.class,
            new BlockValidationException("msg", null));
        assertInstanceOf(BlockchainException.class,
            new ConsensusException("msg"));
        assertInstanceOf(BlockchainException.class,
            new TransactionValidationException("msg",
                ValidationResult.failure(
                    ValidationResult.ValidationStatus.INVALID_SIGNATURE, "bad"), null));
    }

    @Test
    @DisplayName("all exceptions extend RuntimeException (unchecked)")
    void allExceptionsAreUnchecked() {
        assertInstanceOf(RuntimeException.class,
            new BlockValidationException("msg", null));
        assertInstanceOf(RuntimeException.class,
            new ConsensusException("msg"));
    }

    // ─── BlockValidationException ─────────────────────────────────────────────

    @Test
    @DisplayName("BlockValidationException(message, block) stores both fields")
    void blockValidationExceptionMessageAndBlock() {
        Block block = sampleBlock();
        BlockValidationException ex = new BlockValidationException("hash mismatch", block);

        assertEquals("hash mismatch", ex.getMessage());
        assertSame(block, ex.getBlock());
        assertNull(ex.getCause());
    }

    @Test
    @DisplayName("BlockValidationException(message, cause, block) stores all three fields")
    void blockValidationExceptionWithCause() {
        Block block = sampleBlock();
        RuntimeException cause = new RuntimeException("root cause");
        BlockValidationException ex =
            new BlockValidationException("validation failed", cause, block);

        assertEquals("validation failed", ex.getMessage());
        assertSame(cause, ex.getCause());
        assertSame(block, ex.getBlock());
    }

    @Test
    @DisplayName("BlockValidationException accepts null block")
    void blockValidationExceptionNullBlock() {
        BlockValidationException ex = new BlockValidationException("no block available", null);
        assertNull(ex.getBlock());
        assertNotNull(ex.getMessage());
    }

    // ─── ConsensusException ───────────────────────────────────────────────────

    @Test
    @DisplayName("ConsensusException(message) stores message")
    void consensusExceptionMessage() {
        ConsensusException ex = new ConsensusException("quorum not reached");
        assertEquals("quorum not reached", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    @DisplayName("ConsensusException(message, cause) stores message and cause")
    void consensusExceptionWithCause() {
        Throwable cause = new RuntimeException("network timeout");
        ConsensusException ex = new ConsensusException("PBFT failed", cause);

        assertEquals("PBFT failed", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    // ─── TransactionValidationException ──────────────────────────────────────

    @Test
    @DisplayName("TransactionValidationException stores message, result, and transaction")
    void txValidationExceptionAllFields() {
        Transaction tx = sampleTransaction();
        ValidationResult result = ValidationResult.failure(
            ValidationResult.ValidationStatus.INSUFFICIENT_FUNDS, "Not enough");
        TransactionValidationException ex =
            new TransactionValidationException("funds check failed", result, tx);

        assertEquals("funds check failed", ex.getMessage());
        assertSame(result, ex.getValidationResult());
        assertSame(tx, ex.getTransaction());
    }

    @Test
    @DisplayName("TransactionValidationException accepts null transaction")
    void txValidationExceptionNullTransaction() {
        ValidationResult result = ValidationResult.failure(
            ValidationResult.ValidationStatus.DUPLICATE, "dup");
        TransactionValidationException ex =
            new TransactionValidationException("duplicate", result, null);

        assertNull(ex.getTransaction());
        assertNotNull(ex.getValidationResult());
    }

    @Test
    @DisplayName("TransactionValidationException validation result carries status correctly")
    void txValidationExceptionResultStatus() {
        ValidationResult result = ValidationResult.failure(
            ValidationResult.ValidationStatus.INVALID_SIGNATURE, "bad signature");
        TransactionValidationException ex =
            new TransactionValidationException("sig check", result, null);

        assertEquals(ValidationResult.ValidationStatus.INVALID_SIGNATURE,
            ex.getValidationResult().getStatus());
        assertEquals("bad signature", ex.getValidationResult().getErrors().get(0));
    }
}
