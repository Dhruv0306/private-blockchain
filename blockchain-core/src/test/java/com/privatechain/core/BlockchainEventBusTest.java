package com.privatechain.core;

import com.privatechain.core.event.BlockchainEvent;
import com.privatechain.core.event.BlockchainEventBus;
import com.privatechain.core.event.BlockchainEventListener;
import com.privatechain.core.model.Block;
import com.privatechain.core.model.BlockHeader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BlockchainEventBus}.
 *
 * <p>Covers: registration/unregistration, asynchronous delivery, listener isolation
 * (one misbehaving listener must not suppress others), shutdown behavior, and the
 * {@link BlockchainEventBus#awaitQuiescence()} synchronisation helper for tests.</p>
 */
class BlockchainEventBusTest {

    private BlockchainEventBus bus;

    @BeforeEach
    void setUp() {
        bus = new BlockchainEventBus();
    }

    @AfterEach
    void tearDown() {
        if (!bus.isShutdown()) {
            bus.shutdown();
        }
    }

    // ─── Registration ─────────────────────────────────────────────────────────

    @Test
    void registerAndListenerCount() {
        assertEquals(0, bus.listenerCount(), "Fresh bus should have no listeners");

        BlockchainEventListener l1 = event -> {
        };
        BlockchainEventListener l2 = event -> {
        };

        bus.register(l1);
        assertEquals(1, bus.listenerCount());

        bus.register(l2);
        assertEquals(2, bus.listenerCount());
    }

    @Test
    void isRegisteredReturnsTrueForKnownListener() {
        BlockchainEventListener listener = event -> {
        };
        assertFalse(bus.isRegistered(listener));
        bus.register(listener);
        assertTrue(bus.isRegistered(listener));
    }

    @Test
    void unregisterRemovesListener() {
        BlockchainEventListener listener = event -> {
        };
        bus.register(listener);
        bus.unregister(listener);
        assertEquals(0, bus.listenerCount());
        assertFalse(bus.isRegistered(listener));
    }

    @Test
    void unregisterOfNonRegisteredListenerIsNoOp() {
        BlockchainEventListener listener = event -> {
        };
        // Should not throw
        bus.unregister(listener);
        assertEquals(0, bus.listenerCount());
    }

    @Test
    void registerNullThrowsNpe() {
        org.junit.jupiter.api.Assertions.assertThrows(
            NullPointerException.class,
            () -> bus.register(null),
            "register(null) must throw NullPointerException");
    }

    @Test
    void publishNullEventThrowsNpe() {
        org.junit.jupiter.api.Assertions.assertThrows(
            NullPointerException.class,
            () -> bus.publish(null),
            "publish(null) must throw NullPointerException");
    }

    // ─── Async delivery ───────────────────────────────────────────────────────

    @Test
    @Timeout(5)
    void publishDeliversEventToAllListeners() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);

        bus.register(event -> latch.countDown());
        bus.register(event -> latch.countDown());

        bus.publish(buildBlockAddedEvent());

        assertTrue(latch.await(2, TimeUnit.SECONDS),
            "Both listeners should receive the event within 2 s");
    }

    @Test
    @Timeout(5)
    void eventsDeliveredInPublicationOrder() throws InterruptedException {
        List<Integer> received = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(3);

        bus.register(event -> {
            // Each event carries its own numeric index in the block index field
            if (event instanceof BlockchainEvent.BlockAddedEvent e) {
                received.add(e.getBlock().getIndex());
            }
            latch.countDown();
        });

        // Publish three events — single-thread executor must deliver in order
        bus.publish(buildBlockAddedEventWithIndex(0));
        bus.publish(buildBlockAddedEventWithIndex(1));
        bus.publish(buildBlockAddedEventWithIndex(2));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(List.of(0, 1, 2), received,
            "Events must be delivered in publication order");
    }

    @Test
    @Timeout(5)
    void deliveryThreadIsNotCallerThread() throws InterruptedException {
        AtomicReference<Thread> deliveryThread = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        bus.register(event -> {
            deliveryThread.set(Thread.currentThread());
            latch.countDown();
        });

        Thread caller = Thread.currentThread();
        bus.publish(buildBlockAddedEvent());

        assertTrue(latch.await(2, TimeUnit.SECONDS));

        assertNotNull(deliveryThread.get());
        assertNotSame(deliveryThread.get(), caller, "Listener must run on the event-bus thread, not the caller's thread (FR-EVENT-03)");
    }

    // ─── Listener isolation ───────────────────────────────────────────────────

    @Test
    @Timeout(5)
    void throwingListenerDoesNotPreventDeliveryToOthers() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        // First listener throws
        bus.register(event -> {
            throw new RuntimeException("Simulated misbehaving listener");
        });

        // Second listener should still receive the event
        bus.register(event -> latch.countDown());

        bus.publish(buildBlockAddedEvent());

        assertTrue(latch.await(2, TimeUnit.SECONDS),
            "Second listener must receive event even when first one throws");
    }

    @Test
    @Timeout(5)
    void eachListenerReceivesEveryEvent() throws InterruptedException {
        int listenerCount = 5;
        int eventCount = 3;
        CountDownLatch latch = new CountDownLatch(listenerCount * eventCount);
        AtomicInteger counter = new AtomicInteger();

        for (int i = 0; i < listenerCount; i++) {
            bus.register(event -> {
                counter.incrementAndGet();
                latch.countDown();
            });
        }

        for (int i = 0; i < eventCount; i++) {
            bus.publish(buildBlockAddedEventWithIndex(i));
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertEquals(listenerCount * eventCount, counter.get(),
            "Every listener should receive every event");
    }

    // ─── Shutdown ─────────────────────────────────────────────────────────────

    @Test
    void isShutdownReturnsFalseInitially() {
        assertFalse(bus.isShutdown());
    }

    @Test
    void isShutdownReturnsTrueAfterShutdown() {
        bus.shutdown();
        assertTrue(bus.isShutdown());
    }

    @Test
    @Timeout(3)
    void publishAfterShutdownIsDroppedSilently() throws InterruptedException {
        bus.shutdown();

        AtomicInteger delivered = new AtomicInteger();
        bus.register(event -> delivered.incrementAndGet());

        // Should not throw; event should be silently dropped
        bus.publish(buildBlockAddedEvent());

        // Wait a moment then confirm nothing was delivered
        bus.awaitQuiescence(200, TimeUnit.MILLISECONDS);
        assertEquals(0, delivered.get(),
            "Events published after shutdown should be dropped");
    }

    // ─── awaitQuiescence ──────────────────────────────────────────────────────

    @Test
    @Timeout(5)
    void awaitQuiescenceReturnsTrueWhenAllEventsDelivered() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger();
        bus.register(event -> counter.incrementAndGet());

        bus.publish(buildBlockAddedEvent());
        bus.publish(buildBlockAddedEvent());

        boolean quiescent = bus.awaitQuiescence(2, TimeUnit.SECONDS);

        assertTrue(quiescent, "awaitQuiescence should return true when all events delivered");
        assertEquals(2, counter.get(), "Both events should have been delivered");
    }

    @Test
    @Timeout(3)
    void awaitQuiescenceReturnsTrueImmediatelyWhenShutdown() throws InterruptedException {
        bus.shutdown();
        assertTrue(bus.awaitQuiescence(100, TimeUnit.MILLISECONDS),
            "awaitQuiescence after shutdown should return true immediately");
    }

    // ─── Event type coverage ──────────────────────────────────────────────────

    @Test
    @Timeout(5)
    void transactionSubmittedEventIsDelivered() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<BlockchainEvent> captured = new AtomicReference<>();

        bus.register(event -> {
            captured.set(event);
            latch.countDown();
        });

        // Use a minimal anonymous Transaction
        com.privatechain.core.model.Transaction tx = buildMinimalTransaction();
        bus.publish(new BlockchainEvent.TransactionSubmittedEvent(tx));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(captured.get());
        assertInstanceOf(BlockchainEvent.TransactionSubmittedEvent.class, captured.get(), "Received event should be a TransactionSubmittedEvent");
    }

    @Test
    @Timeout(5)
    void peerConnectedEventIsDelivered() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<BlockchainEvent> captured = new AtomicReference<>();

        bus.register(event -> {
            captured.set(event);
            latch.countDown();
        });

        bus.publish(new BlockchainEvent.PeerConnectedEvent("peer-1", "192.168.1.1:8545"));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertInstanceOf(BlockchainEvent.PeerConnectedEvent.class, captured.get());
        assertEquals("peer-1",
            ((BlockchainEvent.PeerConnectedEvent) captured.get()).getPeerId());
    }

    @Test
    @Timeout(5)
    void peerDisconnectedEventIsDelivered() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        bus.register(event -> latch.countDown());
        bus.publish(new BlockchainEvent.PeerDisconnectedEvent("peer-2", "timeout"));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
    }

    @Test
    @Timeout(5)
    void forkDetectedEventIsDelivered() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        bus.register(event -> latch.countDown());
        bus.publish(new BlockchainEvent.ForkDetectedEvent(
            buildBlockWithIndex(1), buildBlockWithIndex(1)));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
    }

    @Test
    @Timeout(5)
    void occurredAtTimestampIsSet() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<BlockchainEvent> captured = new AtomicReference<>();

        bus.register(event -> {
            captured.set(event);
            latch.countDown();
        });

        bus.publish(buildBlockAddedEvent());

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(captured.get().getOccurredAt(),
            "BlockchainEvent.occurredAt must not be null");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private BlockchainEvent.BlockAddedEvent buildBlockAddedEvent() {
        return new BlockchainEvent.BlockAddedEvent(buildBlockWithIndex(0));
    }

    private BlockchainEvent.BlockAddedEvent buildBlockAddedEventWithIndex(int index) {
        return new BlockchainEvent.BlockAddedEvent(buildBlockWithIndex(index));
    }

    private Block buildBlockWithIndex(int index) {
        BlockHeader header = BlockHeader.builder()
            .merkleRoot(BlockHeader.EMPTY_MERKLE_ROOT)
            .build();
        return Block.builder()
            .index(index)
            .previousHash(Block.GENESIS_PREVIOUS_HASH)
            .transactions(List.of())
            .header(header)
            .build();
    }

    private com.privatechain.core.model.Transaction buildMinimalTransaction() {
        // Minimal concrete subclass for testing
        return new com.privatechain.core.model.Transaction(
            java.util.UUID.randomUUID(),
            "sender-address",
            "receiver-address",
            java.math.BigDecimal.ONE,
            java.time.Instant.now(),
            null) {
        };
    }
}
