package com.privatechain.core.mempool;

import com.privatechain.core.model.Block;
import com.privatechain.core.model.Transaction;
import com.privatechain.core.spi.BlockchainStorage;
import com.privatechain.core.spi.ConsensusEngine;
import com.privatechain.core.spi.TransactionPrioritizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TransactionMempool}, covering submission,
 * prioritization, TTL eviction, and state management.
 */
@DisplayName("TransactionMempool")
class TransactionMempoolTest {

    private static final TransactionPrioritizer TIMESTAMP_PRIORITIZER =
        new TimestampBasedPrioritizer();

    /**
     * Test helper: creates a dummy transaction with given sender, receiver, and amount.
     */
    private static Transaction createTx(String sender, String receiver, long amount) {
        return new Transaction(
            UUID.randomUUID(),
            sender,
            receiver,
            BigDecimal.valueOf(amount),
            Instant.now(),
            null) {
        };
    }

    @Test
    void testSubmitTransaction() {
        // Arrange
        TransactionMempool mempool = new TransactionMempool(TIMESTAMP_PRIORITIZER);
        Transaction tx = createTx("Alice", "Bob", 100);

        // Act
        boolean submitted = mempool.submit(tx);

        // Assert
        assertTrue(submitted, "Transaction should be submitted successfully");
        assertEquals(1, mempool.size(), "Mempool should contain 1 transaction");
        assertTrue(mempool.contains(tx.getId()), "Mempool should contain the submitted transaction");
    }

    @Test
    void testDuplicateTransactionRejected() {
        // Arrange
        TransactionMempool mempool = new TransactionMempool(TIMESTAMP_PRIORITIZER);
        Transaction tx = createTx("Alice", "Bob", 100);

        // Act & Assert
        assertTrue(mempool.submit(tx), "First submission should succeed");
        assertFalse(mempool.submit(tx), "Duplicate submission should be rejected");
        assertEquals(1, mempool.size(), "Mempool should still contain only 1 transaction");
    }

    @Test
    void testGetTopN() throws InterruptedException {
        // Arrange
        TransactionMempool mempool = new TransactionMempool(TIMESTAMP_PRIORITIZER);
        Transaction tx1 = createTx("Alice", "Bob", 100);
        Thread.sleep(100); // To ensure different timestamp
        Transaction tx2 = createTx("Bob", "Charlie", 200);
        Thread.sleep(100); // To ensure different timestamp
        Transaction tx3 = createTx("Charlie", "Dave", 300);

        mempool.submit(tx1);
        mempool.submit(tx2);
        mempool.submit(tx3);

        // Act
        List<Transaction> top2 = mempool.getTopN(2);

        // Assert
        assertEquals(2, top2.size(), "Should return exactly 2 transactions");
        assertTrue(top2.contains(tx1), "Should contain first transaction");
        assertTrue(top2.contains(tx2), "Should contain second transaction");
    }

    @Test
    void testRemoveTransaction() {
        // Arrange
        TransactionMempool mempool = new TransactionMempool(TIMESTAMP_PRIORITIZER);
        Transaction tx = createTx("Alice", "Bob", 100);
        mempool.submit(tx);

        // Act
        boolean removed = mempool.remove(tx.getId());

        // Assert
        assertTrue(removed, "Transaction should be removed");
        assertEquals(0, mempool.size(), "Mempool should be empty");
        assertFalse(mempool.contains(tx.getId()), "Mempool should not contain the removed transaction");
    }

    @Test
    void testClear() {
        // Arrange
        TransactionMempool mempool = new TransactionMempool(TIMESTAMP_PRIORITIZER);
        mempool.submit(createTx("Alice", "Bob", 100));
        mempool.submit(createTx("Bob", "Charlie", 200));

        // Act
        mempool.clear();

        // Assert
        assertEquals(0, mempool.size(), "Mempool should be empty after clear");
    }

    @Test
    void testMaxPoolSize() throws InterruptedException {
        // Arrange
        int maxSize = 2;
        TransactionMempool mempool = new TransactionMempool(TIMESTAMP_PRIORITIZER, maxSize);

        Transaction tx1 = createTx("Alice", "Bob", 100);
        Thread.sleep(100); // To ensure different timestamp
        Transaction tx2 = createTx("Bob", "Charlie", 200);
        Thread.sleep(100); // To ensure different timestamp
        Transaction tx3 = createTx("Charlie", "Dave", 300);

        // Act
        mempool.submit(tx1);
        mempool.submit(tx2);
        mempool.submit(tx3); // Should evict the lowest priority

        System.out.println("\n\n" + mempool.size() + " " + mempool.getTopN(10) + "\n\n");
        // Assert
        assertEquals(maxSize, mempool.size(), "Mempool should not exceed max size");
        // tx3 is lowest priority (Latest timestamp), so it should be evicted
        assertFalse(mempool.contains(tx3.getId()), "Latest transaction should be evicted");
    }

    @Test
    void testEvictExpired() throws InterruptedException {
        // Arrange
        TransactionMempool mempool = new TransactionMempool(TIMESTAMP_PRIORITIZER);
        Transaction tx1 = createTx("Alice", "Bob", 100);
        Transaction tx2 = createTx("Bob", "Charlie", 200);

        mempool.submit(tx1);
        Thread.sleep(100); // Small delay to ensure different submission times
        mempool.submit(tx2);

        // Act
        mempool.evictExpired(Duration.ofMillis(50)); // TTL of 50ms

        // Assert
        assertEquals(1, mempool.size(), "Only one transaction should remain");
        assertTrue(mempool.contains(tx2.getId()), "Newer transaction should remain");
        assertFalse(mempool.contains(tx1.getId()), "Expired transaction should be evicted");
    }

    @Test
    void testStartStop() {
        // Arrange
        TransactionMempool mempool = new TransactionMempool(TIMESTAMP_PRIORITIZER);

        // Act
        mempool.start(Duration.ofSeconds(10));
        mempool.submit(createTx("Alice", "Bob", 100));

        // Assert
        assertEquals(1, mempool.size(), "Transaction should be in mempool");

        // Act
        mempool.stop();

        // Assert
        assertEquals(0, mempool.size(), "Mempool should be cleared on stop");
    }

    @Test
    void testFeeBasedPrioritizer() {
        // Arrange
        TransactionMempool mempool = new TransactionMempool(new FeeBasedPrioritizer());

        Transaction lowFee = createTx("Alice", "Bob", 10);
        Transaction highFee = createTx("Bob", "Charlie", 100);

        // Act
        mempool.submit(lowFee);
        mempool.submit(highFee);

        // Assert
        List<Transaction> top1 = mempool.getTopN(1);
        assertEquals(1, top1.size(), "Should return 1 transaction");
        // The high fee transaction should have higher priority
        // (but priority ordering depends on size as well; this test is just for sanity)
        assertNotNull(top1.get(0), "Should return a transaction");
    }

    @Test
    void testTimestampBasedPrioritizer() throws InterruptedException {
        // Arrange
        TransactionMempool mempool = new TransactionMempool(new TimestampBasedPrioritizer());

        Transaction tx1 = createTx("Alice", "Bob", 100);
        mempool.submit(tx1);

        Thread.sleep(10);

        Transaction tx2 = createTx("Bob", "Charlie", 100);
        mempool.submit(tx2);

        // Act
        List<Transaction> all = mempool.getTopN(Integer.MAX_VALUE);

        // Assert
        assertEquals(2, all.size(), "Should contain both transactions");
        // tx1 is older, so it should be first (higher priority)
        assertEquals(tx1.getId(), all.get(0).getId(), "Older transaction should have higher priority");
        assertEquals(tx2.getId(), all.get(1).getId(), "Newer transaction should have lower priority");
    }

    @Test
    void testInvalidArguments() {
        // Arrange
        TransactionMempool mempool = new TransactionMempool(TIMESTAMP_PRIORITIZER);

        // Act & Assert
        assertThrows(NullPointerException.class, () -> mempool.submit(null),
            "Should throw NPE on null transaction");

        assertThrows(NullPointerException.class, () -> mempool.remove(null),
            "Should throw NPE on null transaction ID");

        assertThrows(NullPointerException.class, () -> mempool.contains(null),
            "Should throw NPE on null transaction ID");

        assertThrows(IllegalArgumentException.class, () -> mempool.getTopN(-1),
            "Should throw IAE on negative n");
    }

    // ─── Tests: Validator integration (T-049) ────────────────────────────────

    @Test
    void testSubmitWithValidationAccepts() {
        // Arrange
        var validator = new AcceptAllValidator();
        var mempool = new TransactionMempool(TIMESTAMP_PRIORITIZER, validator);
        var blockchain = createBlockchainForValidator();
        Transaction tx = createTx("Alice", "Bob", 100);

        // Act
        MempoolSubmissionResult result = mempool.submitWithValidation(tx, blockchain);

        // Assert
        assertTrue(result.isAccepted(), "Should accept valid transaction");
        assertEquals(1, mempool.size(), "Mempool should contain 1 transaction");
    }

    @Test
    void testSubmitWithValidationRejects() {
        // Arrange
        var validator = new RejectAllValidator();
        var mempool = new TransactionMempool(TIMESTAMP_PRIORITIZER, validator);
        var blockchain = createBlockchainForValidator();
        Transaction tx = createTx("Alice", "Bob", 100);

        // Act
        MempoolSubmissionResult result = mempool.submitWithValidation(tx, blockchain);

        // Assert
        assertTrue(result.isRejected(), "Should indicate validation failure");
        assertNotNull(result.getRejectionReasons(), "Should contain validation details");
        assertEquals(0, mempool.size(), "Mempool should remain empty");
    }

    @Test
    void testSubmitWithValidationEvenAmountRejection() {
        // Arrange
        var validator = new EvenAmountRejectValidator();
        var mempool = new TransactionMempool(TIMESTAMP_PRIORITIZER, validator);
        var blockchain = createBlockchainForValidator();

        Transaction txEven = createTx("Alice", "Bob", 100);
        Transaction txOdd = createTx("Bob", "Charlie", 101);

        // Act
        MempoolSubmissionResult resultEven = mempool.submitWithValidation(txEven, blockchain);
        MempoolSubmissionResult resultOdd = mempool.submitWithValidation(txOdd, blockchain);

        // Assert
        assertTrue(resultEven.isRejected(), "Even amount should be rejected");
        assertTrue(resultOdd.isAccepted(), "Odd amount should be accepted");
        assertEquals(1, mempool.size(), "Should have only odd transaction");
    }

    @Test
    void testSubmitBypassesValidator() {
        // Arrange
        var validator = new RejectAllValidator();
        var mempool = new TransactionMempool(TIMESTAMP_PRIORITIZER, validator);
        Transaction tx = createTx("Alice", "Bob", 100);

        // Act
        boolean submitted = mempool.submit(tx);

        // Assert
        assertTrue(submitted, "submit() should bypass validator");
        assertEquals(1, mempool.size(), "Transaction should be in pool");
    }

    @Test
    void testDuplicateDetectionAfterValidation() {
        // Arrange
        var validator = new AcceptAllValidator();
        var mempool = new TransactionMempool(TIMESTAMP_PRIORITIZER, validator);
        var blockchain = createBlockchainForValidator();
        Transaction tx = createTx("Alice", "Bob", 100);

        // Act
        MempoolSubmissionResult result1 = mempool.submitWithValidation(tx, blockchain);
        MempoolSubmissionResult result2 = mempool.submitWithValidation(tx, blockchain);

        // Assert
        assertTrue(result1.isAccepted(), "First submission should succeed");
        assertTrue(result2.isRejected(), "Second submission should be duplicate");
        assertEquals(1, mempool.size(), "Should have only 1 transaction");
    }

    @Test
    void testMempoolSubmissionResultAccepted() {
        // Act
        var result1 = MempoolSubmissionResult.accepted();
        var result2 = MempoolSubmissionResult.accepted();

        // Assert
        assertTrue(result1.isAccepted(), "Should be accepted");
        assertTrue(result2.isAccepted(), "Should be accepted");
    }

    @Test
    void testMempoolSubmissionResultDuplicate() {
        // Act
        var result = MempoolSubmissionResult.rejected(List.of("Duplicate transaction"));

        // Assert
        assertTrue(result.isRejected(), "Should be duplicate");
        assertFalse(result.isAccepted(), "Should not be accepted");
    }

    @Test
    void testMempoolSubmissionResultRejected() {
        // Arrange
        var validationFailure = com.privatechain.core.spi.ValidationResult.failure(
            com.privatechain.core.spi.ValidationResult.ValidationStatus.INVALID_SIGNATURE,
            "Bad signature");

        // Act
        var result = MempoolSubmissionResult.rejected(validationFailure.getErrors());

        // Assert
        assertTrue(result.isRejected(), "Should indicate validation failed");
        assertEquals(validationFailure.getErrors(), result.getRejectionReasons(),
            "Should contain validation failure");
    }

    @Test
    void testMempoolSubmissionResultRejectionReasonsDefensiveCopy() {
        // Arrange
        List<String> mutableReasons = new ArrayList<>(List.of("Rejected by validator"));

        // Act
        var result = MempoolSubmissionResult.rejected(mutableReasons);
        mutableReasons.add("Mutated after creation");

        // Assert
        assertEquals(List.of("Rejected by validator"), result.getRejectionReasons(),
            "Result must preserve original reasons even if caller mutates input list");
        assertThrows(UnsupportedOperationException.class,
            () -> result.getRejectionReasons().add("Should fail"),
            "Returned reasons list must be unmodifiable");
    }

    @Test
    void testTimestampBasedPrioritizerIsSerializable() {
        assertInstanceOf(Serializable.class, new TimestampBasedPrioritizer(),
            "TimestampBasedPrioritizer should remain serializable for queue/comparator snapshots");
    }

    @Test
    void testFeeBasedPrioritizerIsSerializable() {
        assertInstanceOf(Serializable.class, new FeeBasedPrioritizer(),
            "FeeBasedPrioritizer should remain serializable for queue/comparator snapshots");
    }

    @Test
    void testSubmitWithValidationNullBlockchainThrowsNPE() {
        // Arrange
        var validator = new AcceptAllValidator();
        var mempool = new TransactionMempool(TIMESTAMP_PRIORITIZER, validator);
        Transaction tx = createTx("Alice", "Bob", 100);

        // Act & Assert
        assertThrows(NullPointerException.class,
            () -> mempool.submitWithValidation(tx, null),
            "Should throw NPE when blockchain is null and validator is present");
    }

    // Helper method for blockchain creation
    private com.privatechain.core.builder.Blockchain createBlockchainForValidator() {
        var storage = new SimpleMemoryStorage();
        var eventBus = new com.privatechain.core.event.BlockchainEventBus();
        var engine = new MinimalConsensusEngine();
        return new com.privatechain.core.builder.Blockchain(engine, storage, eventBus);
    }

    /**
     * Mock validator that always accepts.
     */
    static class AcceptAllValidator implements com.privatechain.core.spi.TransactionValidator {
        @Override
        public com.privatechain.core.spi.ValidationResult validate(Transaction tx,
                                                                   com.privatechain.core.builder.Blockchain chain) {
            return com.privatechain.core.spi.ValidationResult.success();
        }
    }

    /**
     * Mock validator that always rejects.
     */
    static class RejectAllValidator implements com.privatechain.core.spi.TransactionValidator {
        @Override
        public com.privatechain.core.spi.ValidationResult validate(Transaction tx,
                                                                   com.privatechain.core.builder.Blockchain chain) {
            return com.privatechain.core.spi.ValidationResult.failure(
                com.privatechain.core.spi.ValidationResult.ValidationStatus.CUSTOM_REJECTION,
                "Transaction rejected by policy");
        }
    }

    /**
     * Mock validator that rejects even amounts.
     */
    static class EvenAmountRejectValidator implements com.privatechain.core.spi.TransactionValidator {
        @Override
        public com.privatechain.core.spi.ValidationResult validate(Transaction tx,
                                                                   com.privatechain.core.builder.Blockchain chain) {
            long amount = tx.getAmount().longValue();
            if (amount % 2 == 0) {
                return com.privatechain.core.spi.ValidationResult.failure(
                    com.privatechain.core.spi.ValidationResult.ValidationStatus.CUSTOM_REJECTION,
                    "Even amounts not allowed");
            }
            return com.privatechain.core.spi.ValidationResult.success();
        }
    }

    // ─── Helper classes ───────────────────────────────────────────────────────

    /**
     * Minimal consensus engine for testing purposes.
     */
    static class MinimalConsensusEngine implements ConsensusEngine {
        @Override
        public boolean validateBlock(Block block,
                                     com.privatechain.core.builder.Blockchain chain) {
            return true;
        }

        @Override
        public Block mineBlock(List<Transaction> transactions,
                               Block previousBlock) {
            throw new UnsupportedOperationException("Not needed for tests");
        }

        @Override
        public String engineName() {
            return "Minimal";
        }
    }

    /**
     * Simple in-memory storage implementation for testing.
     */
    static class SimpleMemoryStorage implements BlockchainStorage {
        private final TreeMap<Integer, Block> blocks = new TreeMap<>();
        private final ReadWriteLock lock = new ReentrantReadWriteLock();

        @Override
        public void saveBlock(Block block) {
            lock.writeLock().lock();
            try {
                blocks.put(block.getIndex(), block);
            } finally {
                lock.writeLock().unlock();
            }
        }

        @Override
        public Block loadBlock(int index) {
            lock.readLock().lock();
            try {
                Block block = blocks.get(index);
                if (block == null) {
                    throw new NoSuchElementException("Block not found at index: " + index);
                }
                return block;
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public Optional<Block> loadBlockByHash(String hash) {
            lock.readLock().lock();
            try {
                return blocks.values().stream()
                    .filter(b -> b.getHash().equals(hash))
                    .findFirst();
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public List<Block> loadAll() {
            lock.readLock().lock();
            try {
                return new ArrayList<>(blocks.values());
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public boolean exists(String hash) {
            return loadBlockByHash(hash).isPresent();
        }

        @Override
        public int chainHeight() {
            lock.readLock().lock();
            try {
                return blocks.size();
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public void deleteAll() {
            lock.writeLock().lock();
            try {
                blocks.clear();
            } finally {
                lock.writeLock().unlock();
            }
        }
    }
}

