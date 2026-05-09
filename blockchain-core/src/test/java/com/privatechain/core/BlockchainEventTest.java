package com.privatechain.core;

import com.privatechain.core.event.BlockchainEvent;
import com.privatechain.core.event.BlockchainEvent.*;
import com.privatechain.core.model.Block;
import com.privatechain.core.model.BlockHeader;
import com.privatechain.core.model.Transaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BlockchainEvent} and all five permitted subclasses,
 * covering constructors, accessors, validation guards, and toString output.
 */
@DisplayName("BlockchainEvent")
class BlockchainEventTest {

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Block sampleBlock(int index) {
        BlockHeader header = BlockHeader.builder()
            .nonce((long) index)
            .merkleRoot("a".repeat(64))
            .build();
        String prevHash = index == 0
            ? Block.GENESIS_PREVIOUS_HASH
            : "b".repeat(64);
        return Block.builder()
            .index(index)
            .previousHash(prevHash)
            .transactions(List.of())
            .header(header)
            .build();
    }

    private Transaction sampleTransaction() {
        return new Transaction(
            UUID.randomUUID(), "sender", "receiver",
            BigDecimal.ONE, Instant.now(), null) {
        };
    }

    // ─── BlockAddedEvent ──────────────────────────────────────────────────────

    @Test
    @DisplayName("BlockAddedEvent stores block and has non-null occurredAt")
    void blockAddedEventStoresBlock() {
        Block block = sampleBlock(1);
        BlockAddedEvent event = new BlockAddedEvent(block);

        assertEquals(block, event.getBlock());
        assertNotNull(event.getOccurredAt(), "occurredAt must be set by base constructor");
    }

    @Test
    @DisplayName("BlockAddedEvent null block throws NullPointerException")
    void blockAddedEventNullBlockThrows() {
        assertThrows(NullPointerException.class, () -> new BlockAddedEvent(null));
    }

    @Test
    @DisplayName("BlockAddedEvent toString contains block index and hash")
    void blockAddedEventToString() {
        Block block = sampleBlock(5);
        String str = new BlockAddedEvent(block).toString();
        assertTrue(str.contains("5"), "toString should include block index");
        assertTrue(str.contains(block.getHash()), "toString should include block hash");
    }

    // ─── TransactionSubmittedEvent ────────────────────────────────────────────

    @Test
    @DisplayName("TransactionSubmittedEvent stores transaction")
    void txSubmittedEventStoresTransaction() {
        Transaction tx = sampleTransaction();
        TransactionSubmittedEvent event = new TransactionSubmittedEvent(tx);

        assertEquals(tx, event.getTransaction());
        assertNotNull(event.getOccurredAt());
    }

    @Test
    @DisplayName("TransactionSubmittedEvent null transaction throws NullPointerException")
    void txSubmittedEventNullThrows() {
        assertThrows(NullPointerException.class, () -> new TransactionSubmittedEvent(null));
    }

    @Test
    @DisplayName("TransactionSubmittedEvent toString contains transaction id")
    void txSubmittedEventToString() {
        Transaction tx = sampleTransaction();
        String str = new TransactionSubmittedEvent(tx).toString();
        assertTrue(str.contains(tx.getId().toString()));
    }

    // ─── PeerConnectedEvent ───────────────────────────────────────────────────

    @Test
    @DisplayName("PeerConnectedEvent stores peerId and address")
    void peerConnectedEventStoresFields() {
        PeerConnectedEvent event = new PeerConnectedEvent("node-1", "192.168.1.5:8545");

        assertEquals("node-1", event.getPeerId());
        assertEquals("192.168.1.5:8545", event.getAddress());
        assertNotNull(event.getOccurredAt());
    }

    @Test
    @DisplayName("PeerConnectedEvent null peerId throws NullPointerException")
    void peerConnectedNullPeerIdThrows() {
        assertThrows(NullPointerException.class,
            () -> new PeerConnectedEvent(null, "192.168.1.5:8545"));
    }

    @Test
    @DisplayName("PeerConnectedEvent blank peerId throws IllegalArgumentException")
    void peerConnectedBlankPeerIdThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new PeerConnectedEvent("  ", "192.168.1.5:8545"));
    }

    @Test
    @DisplayName("PeerConnectedEvent null address throws NullPointerException")
    void peerConnectedNullAddressThrows() {
        assertThrows(NullPointerException.class,
            () -> new PeerConnectedEvent("node-1", null));
    }

    @Test
    @DisplayName("PeerConnectedEvent blank address throws IllegalArgumentException")
    void peerConnectedBlankAddressThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new PeerConnectedEvent("node-1", "  "));
    }

    @Test
    @DisplayName("PeerConnectedEvent toString contains peerId and address")
    void peerConnectedEventToString() {
        String str = new PeerConnectedEvent("node-abc", "10.0.0.1:8545").toString();
        assertTrue(str.contains("node-abc"));
        assertTrue(str.contains("10.0.0.1:8545"));
    }

    // ─── PeerDisconnectedEvent ────────────────────────────────────────────────

    @Test
    @DisplayName("PeerDisconnectedEvent stores peerId and reason")
    void peerDisconnectedEventStoresFields() {
        PeerDisconnectedEvent event = new PeerDisconnectedEvent("node-2", "timeout");

        assertEquals("node-2", event.getPeerId());
        assertEquals("timeout", event.getReason());
        assertNotNull(event.getOccurredAt());
    }

    @Test
    @DisplayName("PeerDisconnectedEvent accepts null reason (unknown cause)")
    void peerDisconnectedNullReasonIsAllowed() {
        PeerDisconnectedEvent event = new PeerDisconnectedEvent("node-2", null);
        assertNull(event.getReason(), "null reason must be accepted for unknown disconnection cause");
    }

    @Test
    @DisplayName("PeerDisconnectedEvent null peerId throws NullPointerException")
    void peerDisconnectedNullPeerIdThrows() {
        assertThrows(NullPointerException.class,
            () -> new PeerDisconnectedEvent(null, "timeout"));
    }

    @Test
    @DisplayName("PeerDisconnectedEvent blank peerId throws IllegalArgumentException")
    void peerDisconnectedBlankPeerIdThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new PeerDisconnectedEvent("", "timeout"));
    }

    @Test
    @DisplayName("PeerDisconnectedEvent toString contains peerId and reason")
    void peerDisconnectedEventToString() {
        String str = new PeerDisconnectedEvent("node-x", "connection reset").toString();
        assertTrue(str.contains("node-x"));
        assertTrue(str.contains("connection reset"));
    }

    // ─── ForkDetectedEvent ────────────────────────────────────────────────────

    @Test
    @DisplayName("ForkDetectedEvent stores both competing blocks")
    void forkDetectedEventStoresBlocks() {
        Block blockA = sampleBlock(3);
        Block blockB = sampleBlock(4);
        ForkDetectedEvent event = new ForkDetectedEvent(blockA, blockB);

        assertEquals(blockA, event.getBlockA());
        assertEquals(blockB, event.getBlockB());
        assertNotNull(event.getOccurredAt());
    }

    @Test
    @DisplayName("ForkDetectedEvent null blockA throws NullPointerException")
    void forkDetectedNullBlockAThrows() {
        assertThrows(NullPointerException.class,
            () -> new ForkDetectedEvent(null, sampleBlock(1)));
    }

    @Test
    @DisplayName("ForkDetectedEvent null blockB throws NullPointerException")
    void forkDetectedNullBlockBThrows() {
        assertThrows(NullPointerException.class,
            () -> new ForkDetectedEvent(sampleBlock(1), null));
    }

    @Test
    @DisplayName("ForkDetectedEvent toString contains both block hashes")
    void forkDetectedEventToString() {
        Block blockA = sampleBlock(3);
        Block blockB = sampleBlock(4);
        String str = new ForkDetectedEvent(blockA, blockB).toString();
        assertTrue(str.contains(blockA.getHash()), "toString should include blockA hash");
        assertTrue(str.contains(blockB.getHash()), "toString should include blockB hash");
    }

    // ─── Event type identity (Java 17 compatible) ────────────────────────────

    @Test
    @DisplayName("BlockAddedEvent is the correct runtime type and exposes block")
    void blockAddedEventTypeCheck() {
        BlockchainEvent event = new BlockAddedEvent(sampleBlock(5));
        assertInstanceOf(BlockAddedEvent.class, event, "event must be an instance of BlockAddedEvent");
        BlockAddedEvent typed = (BlockAddedEvent) event;
        assertEquals(5, typed.getBlock().getIndex());
    }

    @Test
    @DisplayName("TransactionSubmittedEvent is the correct runtime type and exposes tx")
    void transactionSubmittedEventTypeCheck() {
        Transaction tx = sampleTransaction();
        BlockchainEvent event = new TransactionSubmittedEvent(tx);
        assertInstanceOf(TransactionSubmittedEvent.class, event, "event must be an instance of TransactionSubmittedEvent");
        TransactionSubmittedEvent typed = (TransactionSubmittedEvent) event;
        assertEquals(tx.getId(), typed.getTransaction().getId());
    }

    @Test
    @DisplayName("PeerConnectedEvent is the correct runtime type and exposes peerId")
    void peerConnectedEventTypeCheck() {
        BlockchainEvent event = new PeerConnectedEvent("node-42", "10.0.0.1:8545");
        assertInstanceOf(PeerConnectedEvent.class, event, "event must be an instance of PeerConnectedEvent");
        PeerConnectedEvent typed = (PeerConnectedEvent) event;
        assertEquals("node-42", typed.getPeerId());
    }

    @Test
    @DisplayName("PeerDisconnectedEvent is the correct runtime type and exposes peerId")
    void peerDisconnectedEventTypeCheck() {
        BlockchainEvent event = new PeerDisconnectedEvent("node-7", "timeout");
        assertInstanceOf(PeerDisconnectedEvent.class, event, "event must be an instance of PeerDisconnectedEvent");
        PeerDisconnectedEvent typed = (PeerDisconnectedEvent) event;
        assertEquals("node-7", typed.getPeerId());
    }

    @Test
    @DisplayName("ForkDetectedEvent is the correct runtime type and exposes both blocks")
    void forkDetectedEventTypeCheck() {
        Block a = sampleBlock(3);
        Block b = sampleBlock(4);
        BlockchainEvent event = new ForkDetectedEvent(a, b);
        assertInstanceOf(ForkDetectedEvent.class, event, "event must be an instance of ForkDetectedEvent");
        ForkDetectedEvent typed = (ForkDetectedEvent) event;
        assertEquals(a, typed.getBlockA());
        assertEquals(b, typed.getBlockB());
    }

    @Test
    @DisplayName("all five event types are distinct — no event is an instance of a sibling type")
    void eventTypesAreDistinct() {
        BlockchainEvent blockAdded = new BlockAddedEvent(sampleBlock(0));
        BlockchainEvent txSubmitted = new TransactionSubmittedEvent(sampleTransaction());
        BlockchainEvent peerConn = new PeerConnectedEvent("n1", "host:1234");
        BlockchainEvent peerDisc = new PeerDisconnectedEvent("n2", null);
        BlockchainEvent fork = new ForkDetectedEvent(sampleBlock(0), sampleBlock(1));

        assertFalse(blockAdded instanceof TransactionSubmittedEvent);
        assertFalse(blockAdded instanceof PeerConnectedEvent);
        assertFalse(txSubmitted instanceof BlockAddedEvent);
        assertFalse(peerConn instanceof PeerDisconnectedEvent);
        assertFalse(peerDisc instanceof ForkDetectedEvent);
        assertFalse(fork instanceof BlockAddedEvent);
    }

    // ─── occurredAt is set ────────────────────────────────────────────────────

    @Test
    @DisplayName("getOccurredAt() returns a recent timestamp (within last 5 seconds)")
    void occurredAtIsRecent() {
        Instant before = Instant.now().minusSeconds(1);
        BlockAddedEvent event = new BlockAddedEvent(sampleBlock(0));
        Instant after = Instant.now().plusSeconds(1);

        assertFalse(event.getOccurredAt().isBefore(before), "occurredAt must not be before event construction");
        assertFalse(event.getOccurredAt().isAfter(after), "occurredAt must not be after event construction");
    }
}
