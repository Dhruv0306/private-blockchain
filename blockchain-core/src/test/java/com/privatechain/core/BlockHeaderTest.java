package com.privatechain.core;

import com.privatechain.core.model.BlockHeader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BlockHeader} covering canonical constructor validation,
 * factory methods, and fluent builder paths.
 */
@DisplayName("BlockHeader")
class BlockHeaderTest {

    private static final String VALID_ROOT = "a".repeat(64);

    // ─── Canonical constructor ─────────────────────────────────────────────────

    @Test
    @DisplayName("valid fields construct a BlockHeader successfully")
    void validConstructionSucceeds() {
        BlockHeader h = new BlockHeader(1, 0, 0L, VALID_ROOT, Instant.EPOCH);
        assertEquals(1, h.version());
        assertEquals(0, h.bits());
        assertEquals(0L, h.nonce());
        assertEquals(VALID_ROOT, h.merkleRoot());
        assertEquals(Instant.EPOCH, h.timestamp());
    }

    @Test
    @DisplayName("version < 1 throws IllegalArgumentException")
    void versionZeroThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new BlockHeader(0, 0, 0L, VALID_ROOT, Instant.now()));
    }

    @Test
    @DisplayName("negative version throws IllegalArgumentException")
    void negativeVersionThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new BlockHeader(-1, 0, 0L, VALID_ROOT, Instant.now()));
    }

    @Test
    @DisplayName("negative bits throws IllegalArgumentException")
    void negativeBitsThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new BlockHeader(1, -1, 0L, VALID_ROOT, Instant.now()));
    }

    @Test
    @DisplayName("null merkleRoot throws NullPointerException")
    void nullMerkleRootThrows() {
        assertThrows(NullPointerException.class,
            () -> new BlockHeader(1, 0, 0L, null, Instant.now()));
    }

    @Test
    @DisplayName("blank merkleRoot throws IllegalArgumentException")
    void blankMerkleRootThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new BlockHeader(1, 0, 0L, "  ", Instant.now()));
    }

    @Test
    @DisplayName("null timestamp throws NullPointerException")
    void nullTimestampThrows() {
        assertThrows(NullPointerException.class,
            () -> new BlockHeader(1, 0, 0L, VALID_ROOT, null));
    }

    // ─── Factory methods ──────────────────────────────────────────────────────

    @Test
    @DisplayName("BlockHeader.of() creates header with version 1 and now() timestamp")
    void ofFactoryCreatesVersion1() {
        BlockHeader h = BlockHeader.of(0x1d00ffff, 99L, VALID_ROOT);
        assertEquals(1, h.version());
        assertEquals(0x1d00ffff, h.bits());
        assertEquals(99L, h.nonce());
        assertEquals(VALID_ROOT, h.merkleRoot());
        assertNotNull(h.timestamp());
    }

    @Test
    @DisplayName("BlockHeader.genesis() returns epoch timestamp, zero nonce, and empty merkle root")
    void genesisFactoryReturnsCorrectValues() {
        BlockHeader genesis = BlockHeader.genesis();
        assertEquals(1, genesis.version());
        assertEquals(0L, genesis.nonce());
        assertEquals(Instant.EPOCH, genesis.timestamp());
        assertEquals(BlockHeader.EMPTY_MERKLE_ROOT, genesis.merkleRoot());
        assertEquals(64, genesis.merkleRoot().length());
        assertTrue(genesis.merkleRoot().chars().allMatch(c -> c == '0'));
    }

    // ─── Builder ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("builder with all fields set produces correct header")
    void builderAllFields() {
        Instant ts = Instant.parse("2024-01-01T00:00:00Z");
        BlockHeader h = BlockHeader.builder()
            .version(2)
            .bits(12345)
            .nonce(99999L)
            .merkleRoot(VALID_ROOT)
            .timestamp(ts)
            .build();

        assertEquals(2, h.version());
        assertEquals(12345, h.bits());
        assertEquals(99999L, h.nonce());
        assertEquals(VALID_ROOT, h.merkleRoot());
        assertEquals(ts, h.timestamp());
    }

    @Test
    @DisplayName("builder with only merkleRoot set uses sensible defaults")
    void builderDefaults() {
        BlockHeader h = BlockHeader.builder().merkleRoot(VALID_ROOT).build();

        assertEquals(1, h.version(), "default version must be 1");
        assertEquals(0, h.bits(), "default bits must be 0");
        assertEquals(0L, h.nonce(), "default nonce must be 0");
        assertNotNull(h.timestamp(), "default timestamp must not be null");
    }

    @Test
    @DisplayName("builder produces invalid header if version < 1 (validates on build)")
    void builderValidatesVersion() {
        assertThrows(IllegalArgumentException.class,
            () -> BlockHeader.builder().version(0).merkleRoot(VALID_ROOT).build());
    }

    @Test
    @DisplayName("BlockHeader.builder() returns a new builder instance each call")
    void builderIsFresh() {
        BlockHeader h1 = BlockHeader.builder().nonce(1L).merkleRoot(VALID_ROOT).build();
        BlockHeader h2 = BlockHeader.builder().nonce(2L).merkleRoot(VALID_ROOT).build();
        assertTrue(h1.nonce() != h2.nonce());
    }

    // ─── Record equality ──────────────────────────────────────────────────────

    @Test
    @DisplayName("two BlockHeaders with identical fields are equal (record semantics)")
    void recordEquality() {
        Instant ts = Instant.EPOCH;
        BlockHeader h1 = new BlockHeader(1, 0, 42L, VALID_ROOT, ts);
        BlockHeader h2 = new BlockHeader(1, 0, 42L, VALID_ROOT, ts);
        assertEquals(h1, h2);
        assertEquals(h1.hashCode(), h2.hashCode());
    }

    @Test
    @DisplayName("EMPTY_MERKLE_ROOT constant is 64 zero-hex characters")
    void emptyMerkleRootConstant() {
        assertEquals(64, BlockHeader.EMPTY_MERKLE_ROOT.length());
        assertTrue(BlockHeader.EMPTY_MERKLE_ROOT.chars().allMatch(c -> c == '0'));
    }
}
