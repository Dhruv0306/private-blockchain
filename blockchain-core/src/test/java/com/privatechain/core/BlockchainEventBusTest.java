package com.privatechain.core;

import com.privatechain.core.event.BlockchainEvent;
import com.privatechain.core.event.BlockchainEventBus;
import com.privatechain.core.event.BlockchainEventListener;
import com.privatechain.core.model.Block;
import com.privatechain.core.model.BlockHeader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BlockchainEventBus} covering registration, unregistration,
 * async delivery, exception isolation, and lifecycle.
 */
@DisplayName("BlockchainEventBus")
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

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private BlockchainEvent.BlockAddedEvent sampleEvent() {
        BlockHeader header = BlockHeader.builder()
            .nonce(0L).merkleRoot("a".repeat(64)).build();
        Block block = Block.builder()
            .index(0)
            .previousHash(Block.GENESIS_PREVIOUS_HASH)
            .transactions(List.of())
            .header(header)
            .build();
        return new BlockchainEvent.BlockAddedEvent(block);
    }

    // ─── Registration ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("register increases listener count")
    void registerIncreasesListenerCount() {
        assertEquals(0, bus.listenerCount());
        bus.register(event -> {
        });
        assertEquals(1, bus.listenerCount());
        bus.register(event -> {
        });
        assertEquals(2, bus.listenerCount());
    }

    @Test
    @DisplayName("register with null throws NullPointerException")
    void registerNullThrows() {
        assertThrows(NullPointerException.class, () -> bus.register(null));
    }

    @Test
    @DisplayName("unregister removes a registered listener")
    void unregisterRemovesListener() {
        BlockchainEventListener listener = event -> {
        };
        bus.register(listener);
        assertEquals(1, bus.listenerCount());
        bus.unregister(listener);
        assertEquals(0, bus.listenerCount());
    }

    @Test
    @DisplayName("unregister of non-registered listener is a no-op")
    void unregisterNonRegisteredIsNoOp() {
        bus.unregister(event -> {
        }); // must not throw
        assertEquals(0, bus.listenerCount());
    }

    @Test
    @DisplayName("unregister with null throws NullPointerException")
    void unregisterNullThrows() {
        assertThrows(NullPointerException.class, () -> bus.unregister(null));
    }

    // ─── Publish ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("publish delivers event to all registered listeners")
    void publishDeliversToAllListeners() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        AtomicInteger count = new AtomicInteger(0);

        bus.register(event -> {
            count.incrementAndGet();
            latch.countDown();
        });
        bus.register(event -> {
            count.incrementAndGet();
            latch.countDown();
        });

        bus.publish(sampleEvent());

        assertTrue(latch.await(2, TimeUnit.SECONDS),
            "both listeners must be notified within 2 seconds");
        assertEquals(2, count.get());
    }

    @Test
    @DisplayName("publish with null event throws NullPointerException")
    void publishNullThrows() {
        assertThrows(NullPointerException.class, () -> bus.publish(null));
    }

    @Test
    @DisplayName("listener that throws does not prevent delivery to subsequent listeners")
    void throwingListenerDoesNotBlockOthers() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        // First listener throws
        bus.register(event -> {
            throw new RuntimeException("intentional test exception");
        });
        // Second listener must still be called despite the first throwing
        bus.register(event -> latch.countDown());

        bus.publish(sampleEvent());

        assertTrue(latch.await(2, TimeUnit.SECONDS),
            "second listener must be called even when first throws");
    }

    @Test
    @DisplayName("event delivery is asynchronous — publish returns before listeners run")
    void publishIsAsynchronous() throws InterruptedException {
        // Use a slow listener to confirm publish returns immediately
        AtomicInteger delivered = new AtomicInteger(0);
        CountDownLatch started = new CountDownLatch(1);

        bus.register(event -> {
            started.countDown();
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {
            }
            delivered.incrementAndGet();
        });

        long before = System.currentTimeMillis();
        bus.publish(sampleEvent());
        long after = System.currentTimeMillis();

        // publish() must return in well under 200ms (the listener sleep)
        assertTrue(after - before < 150,
            "publish() must return before the slow listener finishes");

        // Wait for listener to actually run
        started.await(2, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("multiple events are delivered in publication order")
    void eventsDeliveredInOrder() throws InterruptedException {
        List<Integer> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(3);

        bus.register(event -> {
            if (event instanceof BlockchainEvent.BlockAddedEvent e) {
                received.add(e.getBlock().getIndex());
                latch.countDown();
            }
        });

        for (int i = 0; i < 3; i++) {
            BlockHeader header = BlockHeader.builder()
                .nonce((long) i).merkleRoot("a".repeat(64)).build();
            Block block = Block.builder()
                .index(i)
                .previousHash(Block.GENESIS_PREVIOUS_HASH)
                .transactions(List.of())
                .header(header)
                .build();
            bus.publish(new BlockchainEvent.BlockAddedEvent(block));
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertEquals(List.of(0, 1, 2), received,
            "events must be delivered in publication order");
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("isShutdown returns false before shutdown, true after")
    void shutdownChangesState() {
        assertFalse(bus.isShutdown());
        bus.shutdown();
        assertTrue(bus.isShutdown());
    }

    @Test
    @DisplayName("publish after shutdown drops event silently (no exception)")
    void publishAfterShutdownDropsSilently() {
        bus.shutdown();
        // Must not throw — post-shutdown publish is silently dropped
        bus.publish(sampleEvent());
    }

    @Test
    @DisplayName("shutdown can be called multiple times without error")
    void doubleShutdownIsIdempotent() {
        bus.shutdown();
        bus.shutdown(); // must not throw
        assertTrue(bus.isShutdown());
    }
}
