package com.privatechain.core;

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
 * Unit tests for {@link Block} covering construction, immutability, hash computation,
 * and chain linkage validation.
 */
@DisplayName("Block")
class BlockTest {

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private BlockHeader sampleHeader() {
        return BlockHeader.builder()
            .version(1)
            .nonce(42L)
            .merkleRoot("a".repeat(64))
            .timestamp(Instant.parse("2025-01-01T00:00:00Z"))
            .build();
    }

    private Transaction sampleTransaction() {
        return new Transaction(
            UUID.randomUUID(), "sender1", "receiver1",
            BigDecimal.ONE, Instant.now(), null) {
        };
    }

    // ─── Construction ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("builder creates block with computed hash")
    void builderComputesHash() {
        Block block = Block.builder()
            .index(0)
            .previousHash(Block.GENESIS_PREVIOUS_HASH)
            .transactions(List.of())
            .header(sampleHeader())
            .build();

        assertNotNull(block.getHash(), "hash must not be null");
        assertFalse(block.getHash().isBlank(), "hash must not be blank");
        assertEquals(64, block.getHash().length(), "SHA-256 hex must be 64 chars");
    }

    @Test
    @DisplayName("same inputs always produce the same hash")
    void hashIsDeterministic() {
        BlockHeader header = sampleHeader();
        String prevHash = Block.GENESIS_PREVIOUS_HASH;

        Block b1 = Block.builder().index(0).previousHash(prevHash).header(header).build();
        Block b2 = Block.builder().index(0).previousHash(prevHash).header(header).build();

        assertEquals(b1.getHash(), b2.getHash(), "same inputs must produce identical hashes");
    }

    @Test
    @DisplayName("different nonce produce different hashes")
    void differentNonceProduceDifferentHashes() {
        BlockHeader h1 = BlockHeader.builder().nonce(1L).merkleRoot("a".repeat(64)).build();
        BlockHeader h2 = BlockHeader.builder().nonce(2L).merkleRoot("a".repeat(64)).build();

        Block b1 = Block.builder().index(0).previousHash(Block.GENESIS_PREVIOUS_HASH).header(h1).build();
        Block b2 = Block.builder().index(0).previousHash(Block.GENESIS_PREVIOUS_HASH).header(h2).build();

        assertNotEquals(b1.getHash(), b2.getHash());
    }

    @Test
    @DisplayName("transactions list is unmodifiable")
    void transactionsListIsUnmodifiable() {
        Block block = Block.builder()
            .index(0)
            .previousHash(Block.GENESIS_PREVIOUS_HASH)
            .transactions(List.of(sampleTransaction()))
            .header(sampleHeader())
            .build();

        assertThrows(UnsupportedOperationException.class, () -> block.getTransactions().clear(),
            "transactions list must be unmodifiable");
    }

    @Test
    @DisplayName("negative index throws IllegalArgumentException")
    void negativeIndexThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> Block.builder().index(-1).build());
    }

    @Test
    @DisplayName("null previousHash throws NullPointerException")
    void nullPreviousHashThrows() {
        assertThrows(NullPointerException.class,
            () -> Block.builder().previousHash(null).build());
    }

    // ─── Hash validation ──────────────────────────────────────────────────────

    @Test
    @DisplayName("isHashValid returns true for freshly built block")
    void freshBlockHashIsValid() {
        Block block = Block.builder()
            .index(1)
            .previousHash("a".repeat(64))
            .header(sampleHeader())
            .build();

        assertTrue(block.isHashValid());
    }

    @Test
    @DisplayName("manually constructed block with wrong hash fails isHashValid")
    void tamperedHashFailsValidation() {
        // Construct a block with a deliberately wrong hash
        Block tampered = new Block(0, sampleHeader(), Block.GENESIS_PREVIOUS_HASH,
            "deadbeef".repeat(8), List.of());

        assertFalse(tampered.isHashValid(), "tampered hash must fail validation");
    }

    // ─── Chain linkage ────────────────────────────────────────────────────────

    @Test
    @DisplayName("genesis block has correct previousHash sentinel")
    void genesisPreviousHash() {
        Block genesis = Block.builder()
            .index(0)
            .previousHash(Block.GENESIS_PREVIOUS_HASH)
            .header(sampleHeader())
            .build();

        assertEquals(Block.GENESIS_PREVIOUS_HASH, genesis.getPreviousHash());
    }

    @Test
    @DisplayName("linked block carries previous block hash correctly")
    void chainLinkage() {
        Block genesis = Block.builder().index(0)
            .previousHash(Block.GENESIS_PREVIOUS_HASH).header(sampleHeader()).build();

        Block second = Block.builder().index(1)
            .previousHash(genesis.getHash()).header(sampleHeader()).build();

        assertEquals(genesis.getHash(), second.getPreviousHash());
    }

    // ─── Equality and toString ────────────────────────────────────────────────

    @Test
    @DisplayName("two blocks with same hash are equal")
    void equalityByHash() {
        BlockHeader header = sampleHeader();
        Block b1 = Block.builder().index(0).previousHash(Block.GENESIS_PREVIOUS_HASH).header(header).build();
        Block b2 = Block.builder().index(0).previousHash(Block.GENESIS_PREVIOUS_HASH).header(header).build();

        assertEquals(b1, b2);
        assertEquals(b1.hashCode(), b2.hashCode());
    }

    @Test
    @DisplayName("toString includes index and transaction count")
    void toStringIsInformative() {
        Block block = Block.builder().index(3)
            .previousHash(Block.GENESIS_PREVIOUS_HASH)
            .transactions(List.of(sampleTransaction(), sampleTransaction()))
            .header(sampleHeader())
            .build();

        String str = block.toString();
        assertTrue(str.contains("3"), "toString should include index");
        assertTrue(str.contains("2"), "toString should include txCount");
    }
}
