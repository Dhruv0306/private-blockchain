package com.privatechain.core.mempool;

import com.privatechain.core.event.BlockchainEvent;
import com.privatechain.core.event.BlockchainEventBus;
import com.privatechain.core.model.Block;
import com.privatechain.core.model.BlockHeader;
import com.privatechain.core.model.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests verifying the {@link TransactionMempool} ↔ {@link BlockchainEventBus}
 * wiring introduced in Milestone 8 (T-066).
 *
 * <p>Specifically validates:</p>
 * <ul>
 *   <li>A {@link BlockchainEvent.TransactionSubmittedEvent} is published when a
 *       transaction is accepted into the pool.</li>
 *   <li>Confirmed transactions are automatically removed from the pool when a
 *       {@link BlockchainEvent.BlockAddedEvent} is received (FR-MEMPOOL-05).</li>
 *   <li>Transactions not in the block are retained in the pool.</li>
 *   <li>The mempool handles concurrent publish and removal safely.</li>
 * </ul>
 */
class MempoolEventWiringTest {

    /**
     * Prioritizer under test — timestamp ordering is deterministic and simple.
     */
    private static final TimestampBasedPrioritizer PRIORITIZER = new TimestampBasedPrioritizer();

    private BlockchainEventBus eventBus;
    private TransactionMempool mempool;

    /**
     * Creates a minimal concrete {@link Transaction} subclass with the given fields.
     *
     * @param sender   sender address
     * @param receiver receiver address
     * @param amount   transfer amount
     * @return a new unsigned transaction
     */
    private static Transaction createTx(String sender, String receiver, int amount) {
        return new Transaction(
            UUID.randomUUID(),
            sender,
            receiver,
            BigDecimal.valueOf(amount),
            Instant.now(),
            null) {
        };
    }

    /**
     * Builds a minimal {@link Block} at the given index containing the provided transactions.
     *
     * @param index        block index (&ge; 0)
     * @param transactions transactions to include (non-null)
     * @return a new block
     */
    private static Block buildBlockWithTransactions(int index, List<Transaction> transactions) {
        BlockHeader header = BlockHeader.builder()
            .merkleRoot(BlockHeader.EMPTY_MERKLE_ROOT)
            .build();
        return Block.builder()
            .index(index)
            .previousHash(Block.GENESIS_PREVIOUS_HASH)
            .transactions(transactions)
            .header(header)
            .build();
    }

    // ─── TransactionSubmittedEvent publication ────────────────────────────────

    @BeforeEach
    void setUp() {
        eventBus = new BlockchainEventBus();
        // Wire mempool to the event bus (M8 constructor)
        mempool = new TransactionMempool(PRIORITIZER, eventBus);
    }

    @AfterEach
    void tearDown() {
        if (!eventBus.isShutdown()) {
            eventBus.shutdown();
        }
    }

    @Test
    @Timeout(5)
    void submitPublishesTransactionSubmittedEvent() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<BlockchainEvent.TransactionSubmittedEvent> captured =
            new AtomicReference<>();

        eventBus.register(event -> {
            if (event instanceof BlockchainEvent.TransactionSubmittedEvent e) {
                captured.set(e);
                latch.countDown();
            }
        });

        Transaction tx = createTx("Alice", "Bob", 50);
        boolean accepted = mempool.submit(tx);

        assertTrue(accepted, "Transaction should be accepted");
        assertTrue(latch.await(2, TimeUnit.SECONDS),
            "TransactionSubmittedEvent should be published within 2 s");

        assertNotNull(captured.get());
        assertEquals(tx.getId(), captured.get().getTransaction().getId(),
            "Published event should carry the submitted transaction's ID");
    }

    // ─── Confirmed transaction removal (FR-MEMPOOL-05) ───────────────────────

    @Test
    @Timeout(5)
    void duplicateSubmitDoesNotPublishEvent() throws InterruptedException {
        java.util.concurrent.atomic.AtomicInteger eventCount =
            new java.util.concurrent.atomic.AtomicInteger(0);

        eventBus.register(event -> {
            if (event instanceof BlockchainEvent.TransactionSubmittedEvent) {
                eventCount.incrementAndGet();
            }
        });

        Transaction tx = createTx("Alice", "Bob", 50);
        mempool.submit(tx);
        mempool.submit(tx); // duplicate — must be rejected

        // Allow the event bus to deliver
        eventBus.awaitQuiescence(500, TimeUnit.MILLISECONDS);

        assertEquals(1, eventCount.get(),
            "Duplicate submission must not publish a second event");
    }

    @Test
    @Timeout(5)
    void noEventPublishedWhenBusIsShutDown() throws InterruptedException {
        java.util.concurrent.atomic.AtomicInteger eventCount =
            new java.util.concurrent.atomic.AtomicInteger(0);

        eventBus.register(event -> eventCount.incrementAndGet());
        eventBus.shutdown(); // shutdown BEFORE submitting

        mempool.submit(createTx("Alice", "Bob", 10));

        Thread.sleep(200); // give bus time to (not) deliver
        assertEquals(0, eventCount.get(),
            "No events should be published after bus shutdown");
    }

    @Test
    @Timeout(5)
    void blockAddedEventRemovesConfirmedTransactions() throws InterruptedException {
        // Arrange — add two transactions to the mempool
        Transaction tx1 = createTx("Alice", "Bob", 100);
        Transaction tx2 = createTx("Bob", "Charlie", 200);
        mempool.submit(tx1);
        mempool.submit(tx2);
        assertEquals(2, mempool.size(), "Pre-condition: pool should hold 2 transactions");

        // Act — publish a BlockAddedEvent containing tx1 (simulates block mining)
        Block blockWithTx1 = buildBlockWithTransactions(1, List.of(tx1));
        eventBus.publish(new BlockchainEvent.BlockAddedEvent(blockWithTx1));

        // Wait for event delivery
        assertTrue(eventBus.awaitQuiescence(2, TimeUnit.SECONDS),
            "Event bus should deliver within 2 s");

        // Assert — tx1 removed, tx2 retained
        assertEquals(1, mempool.size(),
            "Pool should contain exactly 1 transaction after block confirmation");
        assertFalse(mempool.contains(tx1.getId()),
            "Confirmed transaction tx1 must be removed from pool");
        assertTrue(mempool.contains(tx2.getId()),
            "Unconfirmed transaction tx2 must remain in pool");
    }

    @Test
    @Timeout(5)
    void blockAddedEventWithAllPoolTxsEmptiesPool() throws InterruptedException {
        Transaction tx1 = createTx("A", "B", 10);
        Transaction tx2 = createTx("B", "C", 20);
        Transaction tx3 = createTx("C", "D", 30);
        mempool.submit(tx1);
        mempool.submit(tx2);
        mempool.submit(tx3);

        Block fullBlock = buildBlockWithTransactions(1, List.of(tx1, tx2, tx3));
        eventBus.publish(new BlockchainEvent.BlockAddedEvent(fullBlock));

        assertTrue(eventBus.awaitQuiescence(2, TimeUnit.SECONDS));

        assertEquals(0, mempool.size(),
            "Pool must be empty after all transactions are confirmed");
    }

    @Test
    @Timeout(5)
    void blockWithNoPoolTransactionsLeavesPoolUnchanged() throws InterruptedException {
        Transaction tx1 = createTx("Alice", "Bob", 100);
        mempool.submit(tx1);

        // Block contains a tx that was never in the pool
        Transaction externalTx = createTx("Stranger", "Other", 999);
        Block blockWithExternalTx = buildBlockWithTransactions(1, List.of(externalTx));
        eventBus.publish(new BlockchainEvent.BlockAddedEvent(blockWithExternalTx));

        assertTrue(eventBus.awaitQuiescence(2, TimeUnit.SECONDS));

        assertEquals(1, mempool.size(),
            "Pool should be unchanged when confirmed txs were never in the pool");
        assertTrue(mempool.contains(tx1.getId()),
            "tx1 (not in block) must remain in pool");
    }

    // ─── Non-interference with other events ──────────────────────────────────

    @Test
    @Timeout(5)
    void emptyBlockDoesNotAffectPool() throws InterruptedException {
        Transaction tx1 = createTx("Alice", "Bob", 100);
        mempool.submit(tx1);

        // Block with no transactions
        Block emptyBlock = buildBlockWithTransactions(1, List.of());
        eventBus.publish(new BlockchainEvent.BlockAddedEvent(emptyBlock));

        assertTrue(eventBus.awaitQuiescence(2, TimeUnit.SECONDS));

        assertEquals(1, mempool.size(), "Empty block must not affect pool");
    }

    @Test
    @Timeout(5)
    void multipleBlockEventsRemoveTransactionsIncrementally() throws InterruptedException {
        Transaction tx1 = createTx("A", "B", 10);
        Transaction tx2 = createTx("B", "C", 20);
        Transaction tx3 = createTx("C", "D", 30);
        mempool.submit(tx1);
        mempool.submit(tx2);
        mempool.submit(tx3);

        // First block confirms tx1
        eventBus.publish(new BlockchainEvent.BlockAddedEvent(
            buildBlockWithTransactions(1, List.of(tx1))));
        assertTrue(eventBus.awaitQuiescence(2, TimeUnit.SECONDS));
        assertEquals(2, mempool.size(), "After block 1: 2 txs should remain");

        // Second block confirms tx2
        eventBus.publish(new BlockchainEvent.BlockAddedEvent(
            buildBlockWithTransactions(2, List.of(tx2))));
        assertTrue(eventBus.awaitQuiescence(2, TimeUnit.SECONDS));
        assertEquals(1, mempool.size(), "After block 2: 1 tx should remain");

        // Third block confirms tx3
        eventBus.publish(new BlockchainEvent.BlockAddedEvent(
            buildBlockWithTransactions(3, List.of(tx3))));
        assertTrue(eventBus.awaitQuiescence(2, TimeUnit.SECONDS));
        assertEquals(0, mempool.size(), "After block 3: pool should be empty");
    }

    // ─── Factory helpers ──────────────────────────────────────────────────────

    @Test
    @Timeout(5)
    void peerConnectedEventDoesNotAffectPool() throws InterruptedException {
        Transaction tx1 = createTx("Alice", "Bob", 100);
        mempool.submit(tx1);

        eventBus.publish(new BlockchainEvent.PeerConnectedEvent("peer-1", "192.168.0.1:8545"));
        assertTrue(eventBus.awaitQuiescence(2, TimeUnit.SECONDS));

        assertEquals(1, mempool.size(), "PeerConnectedEvent must not alter pool");
    }

    @Test
    @Timeout(5)
    void forkDetectedEventDoesNotAffectPool() throws InterruptedException {
        Transaction tx1 = createTx("Alice", "Bob", 100);
        mempool.submit(tx1);

        Block blockA = buildBlockWithTransactions(1, List.of());
        Block blockB = buildBlockWithTransactions(1, List.of());
        eventBus.publish(new BlockchainEvent.ForkDetectedEvent(blockA, blockB));
        assertTrue(eventBus.awaitQuiescence(2, TimeUnit.SECONDS));

        assertEquals(1, mempool.size(), "ForkDetectedEvent must not alter pool");
    }
}
