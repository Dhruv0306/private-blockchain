package com.privatechain.core;

import com.privatechain.core.event.BlockchainEventBus;
import com.privatechain.core.event.BlockchainEvent;
import com.privatechain.core.event.BlockchainEventListener;
import com.privatechain.core.model.Block;
import com.privatechain.core.model.BlockHeader;
import com.privatechain.core.model.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for observability concerns introduced in Milestone 8 (T-067).
 *
 * <p>Verifies that the SLF4J/JUL log statements emitted by the blockchain subsystems
 * are present, at the correct log level, and contain expected content.  Also serves
 * as a thin integration test ensuring that peer connects/disconnect events flow through
 * the {@link BlockchainEventBus} correctly.</p>
 *
 * <h2>Why JUL (java.util.logging)?</h2>
 * <p>{@code blockchain-core} has zero mandatory transitive dependencies (design.md §7.1),
 * so production code uses the JDK-bundled {@code java.util.logging} API. In projects
 * that use SLF4J with a JUL bridge ({@code jul-to-slf4j}), these records flow into
 * whatever backend (Logback, Log4j2, etc.) is on the classpath. Tests intercept JUL
 * records directly to avoid an additional test dependency.</p>
 */
class NodeObservabilityTest {

    // ─── Log capture infrastructure ───────────────────────────────────────────

    /**
     * In-memory JUL {@link Handler} that accumulates {@link LogRecord}s for assertions.
     */
    private static final class CapturingHandler extends Handler {

        private final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            synchronized (records) {
                records.add(record);
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            synchronized (records) {
                records.clear();
            }
        }

        /**
         * Returns a snapshot of all captured log records.
         */
        List<LogRecord> snapshot() {
            synchronized (records) {
                return List.copyOf(records);
            }
        }

        /**
         * Returns {@code true} if any captured record's message contains
         * {@code fragment} (case-insensitive).
         *
         * @param fragment the substring to search for
         * @return {@code true} if found in at least one record
         */
        boolean containsMessage(String fragment) {
            String lower = fragment.toLowerCase();
            synchronized (records) {
                return records.stream()
                    .map(r -> r.getMessage() == null ? "" : r.getMessage().toLowerCase())
                    .anyMatch(msg -> msg.contains(lower));
            }
        }

        /**
         * Returns the count of records at or above the given level.
         *
         * @param level the minimum level
         * @return count of matching records
         */
        long countAtLevel(Level level) {
            synchronized (records) {
                return records.stream()
                    .filter(r -> r.getLevel().intValue() >= level.intValue())
                    .count();
            }
        }
    }

    // ─── Test state ───────────────────────────────────────────────────────────

    private BlockchainEventBus eventBus;
    private CapturingHandler capturingHandler;

    /**
     * Loggers under observation — one per subsystem tested here.
     */
    private Logger eventBusLogger;

    @BeforeEach
    void setUp() {
        eventBus = new BlockchainEventBus();
        capturingHandler = new CapturingHandler();
        capturingHandler.setLevel(Level.ALL);

        // Attach the capturing handler to the BlockchainEventBus logger
        eventBusLogger = Logger.getLogger(BlockchainEventBus.class.getName());
        eventBusLogger.addHandler(capturingHandler);
        eventBusLogger.setLevel(Level.ALL);
        // Prevent records from propagating to the root logger during tests
        eventBusLogger.setUseParentHandlers(false);
    }

    @AfterEach
    void tearDown() {
        eventBusLogger.removeHandler(capturingHandler);
        eventBusLogger.setUseParentHandlers(true);
        if (!eventBus.isShutdown()) {
            eventBus.shutdown();
        }
    }

    // ─── EventBus logging (T-067) ─────────────────────────────────────────────

    @Test
    void shutdownLogsInfoMessage() {
        eventBus.shutdown();

        assertTrue(
            capturingHandler.containsMessage("shut down"),
            "shutdown() must log an INFO message containing 'shut down'");
    }

    @Test
    @Timeout(5)
    void registerLogsAtFineLevel() {
        eventBus.register(event -> {
        });

        // FINE records are only present if the logger level allows them
        List<LogRecord> records = capturingHandler.snapshot();
        boolean hasFineOrBelow = records.stream()
            .anyMatch(r -> r.getLevel().intValue() <= Level.FINE.intValue());
        // Only assert if FINE logging is enabled (it is in setUp)
        assertTrue(hasFineOrBelow,
            "register() should emit at least one FINE or lower log record");
    }

    @Test
    @Timeout(5)
    void publishAfterShutdownLogsWarning() throws InterruptedException {
        eventBus.shutdown();
        eventBus.publish(buildBlockAddedEvent(0));

        Thread.sleep(100); // brief pause — executor is shut down so delivery is sync-dropped

        assertTrue(
            capturingHandler.countAtLevel(Level.WARNING) >= 1,
            "Publishing after shutdown must emit at least one WARNING log record");
        assertTrue(
            capturingHandler.containsMessage("shut down") ||
                capturingHandler.containsMessage("dropping"),
            "WARNING message should mention shutdown or dropping the event");
    }

    // ─── Peer event logging integration (T-066 / T-067) ──────────────────────

    @Test
    @Timeout(5)
    void peerConnectedEventCarriesCorrectPeerInfo() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        List<BlockchainEvent> received = new ArrayList<>();

        eventBus.register(event -> {
            received.add(event);
            latch.countDown();
        });

        String nodeId = "peer-abc";
        String address = "10.0.0.5:8545";
        eventBus.publish(new BlockchainEvent.PeerConnectedEvent(nodeId, address));

        assertTrue(latch.await(2, TimeUnit.SECONDS));

        assertEquals(1, received.size());
        BlockchainEvent.PeerConnectedEvent ev =
            (BlockchainEvent.PeerConnectedEvent) received.get(0);
        assertEquals(nodeId, ev.getPeerId(),
            "PeerConnectedEvent must carry the correct nodeId");
        assertEquals(address, ev.getAddress(),
            "PeerConnectedEvent must carry the correct address");
        assertNotNull(ev.getOccurredAt(), "PeerConnectedEvent.occurredAt must be set");
    }

    @Test
    @Timeout(5)
    void peerDisconnectedEventCarriesNodeIdAndReason() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        List<BlockchainEvent> received = new ArrayList<>();

        eventBus.register(event -> {
            received.add(event);
            latch.countDown();
        });

        String nodeId = "peer-xyz";
        String reason = "heartbeat timeout";
        eventBus.publish(new BlockchainEvent.PeerDisconnectedEvent(nodeId, reason));

        assertTrue(latch.await(2, TimeUnit.SECONDS));

        BlockchainEvent.PeerDisconnectedEvent ev =
            (BlockchainEvent.PeerDisconnectedEvent) received.get(0);
        assertEquals(nodeId, ev.getPeerId());
        assertEquals(reason, ev.getReason());
    }

    // ─── Multi-listener observability ────────────────────────────────────────

    @Test
    @Timeout(5)
    void allListenersReceiveSameEventType() throws InterruptedException {
        int n = 4;
        CountDownLatch latch = new CountDownLatch(n);
        AtomicInteger blockAddedCount = new AtomicInteger(0);

        for (int i = 0; i < n; i++) {
            eventBus.register(event -> {
                if (event instanceof BlockchainEvent.BlockAddedEvent) {
                    blockAddedCount.incrementAndGet();
                }
                latch.countDown();
            });
        }

        eventBus.publish(buildBlockAddedEvent(42));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(n, blockAddedCount.get(),
            "All " + n + " listeners must receive the BlockAddedEvent");
    }

    @Test
    @Timeout(5)
    void mixedEventTypesDeliveredInOrder() throws InterruptedException {
        List<String> types = java.util.Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(3);

        eventBus.register(event -> {
            types.add(event.getClass().getSimpleName());
            latch.countDown();
        });

        eventBus.publish(buildBlockAddedEvent(1));
        eventBus.publish(new BlockchainEvent.PeerConnectedEvent("p1", "127.0.0.1:9000"));
        eventBus.publish(new BlockchainEvent.TransactionSubmittedEvent(buildMinimalTx()));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(
            List.of("BlockAddedEvent", "PeerConnectedEvent", "TransactionSubmittedEvent"),
            types,
            "Mixed event types must arrive in publication order");
    }

    // ─── ForkDetectedEvent observability ─────────────────────────────────────

    @Test
    @Timeout(5)
    void forkDetectedEventCarriesBothBlocks() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        List<BlockchainEvent> received = new ArrayList<>();

        eventBus.register(event -> {
            received.add(event);
            latch.countDown();
        });

        Block blockA = buildBlockWithIndex(5);
        Block blockB = buildBlockWithIndex(5);
        eventBus.publish(new BlockchainEvent.ForkDetectedEvent(blockA, blockB));

        assertTrue(latch.await(2, TimeUnit.SECONDS));

        BlockchainEvent.ForkDetectedEvent ev =
            (BlockchainEvent.ForkDetectedEvent) received.get(0);
        assertEquals(blockA.getHash(), ev.getBlockA().getHash());
        assertEquals(blockB.getHash(), ev.getBlockB().getHash());
    }

    // ─── Logging for other subsystems (T-067 general contract) ───────────────

    @Test
    void noErrorLevelLogsUnderNormalOperation() throws InterruptedException {
        // Attach our handler to the root logger to capture all subsystem logs
        Logger root = Logger.getLogger("");
        root.addHandler(capturingHandler);

        try {
            // Normal operations: register, publish, await, unregister
            BlockchainEventListener listener = event -> {
            };
            eventBus.register(listener);
            eventBus.publish(buildBlockAddedEvent(0));
            eventBus.awaitQuiescence(1, TimeUnit.SECONDS);
            eventBus.unregister(listener);

            // There should be no SEVERE records under normal operation
            long severeCount = capturingHandler.countAtLevel(Level.SEVERE);
            assertEquals(0, severeCount,
                "No SEVERE log records should be emitted under normal operation");
        } finally {
            root.removeHandler(capturingHandler);
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static BlockchainEvent.BlockAddedEvent buildBlockAddedEvent(int index) {
        return new BlockchainEvent.BlockAddedEvent(buildBlockWithIndex(index));
    }

    private static Block buildBlockWithIndex(int index) {
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

    private static Transaction buildMinimalTx() {
        return new Transaction(
            UUID.randomUUID(),
            "sender",
            "receiver",
            BigDecimal.TEN,
            Instant.now(),
            null) {
        };
    }
}
