package com.privatechain.core;

import com.privatechain.core.builder.GenesisBlockFactory;
import com.privatechain.core.model.Block;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link GenesisBlockFactory} covering determinism, structure,
 * and validation constraints (FR-CORE-07).
 */
@DisplayName("GenesisBlockFactory")
class GenesisBlockFactoryTest {

    @Test
    @DisplayName("genesis block has index 0")
    void genesisHasIndexZero() {
        Block genesis = GenesisBlockFactory.create("test");
        assertEquals(0, genesis.getIndex());
    }

    @Test
    @DisplayName("genesis block previousHash is all-zeros sentinel")
    void genesisPreviousHashIsAllZeros() {
        Block genesis = GenesisBlockFactory.create("test");
        assertEquals(Block.GENESIS_PREVIOUS_HASH, genesis.getPreviousHash());
        assertEquals(64, genesis.getPreviousHash().length());
        assertTrue(genesis.getPreviousHash().chars().allMatch(c -> c == '0'));
    }

    @Test
    @DisplayName("genesis block contains no transactions")
    void genesisHasNoTransactions() {
        Block genesis = GenesisBlockFactory.create("test");
        assertTrue(genesis.getTransactions().isEmpty());
    }

    @Test
    @DisplayName("genesis block hash is valid (not tampered)")
    void genesisHashIsValid() {
        Block genesis = GenesisBlockFactory.create("test");
        assertTrue(genesis.isHashValid());
    }

    @Test
    @DisplayName("same chainId always produces the same genesis hash")
    void genesisIsDeterministic() {
        Block g1 = GenesisBlockFactory.create("acme-chain-v1");
        Block g2 = GenesisBlockFactory.create("acme-chain-v1");
        assertEquals(g1.getHash(), g2.getHash());
    }

    @Test
    @DisplayName("different chainIds produce different genesis hashes")
    void differentChainIdsDifferentHashes() {
        Block g1 = GenesisBlockFactory.create("chain-a");
        Block g2 = GenesisBlockFactory.create("chain-b");
        assertNotEquals(g1.getHash(), g2.getHash());
    }

    @Test
    @DisplayName("createDefault() returns non-null genesis block")
    void createDefaultIsNonNull() {
        Block genesis = GenesisBlockFactory.createDefault();
        assertNotNull(genesis);
        assertEquals(0, genesis.getIndex());
    }

    @Test
    @DisplayName("null chainId throws NullPointerException")
    void nullChainIdThrows() {
        assertThrows(NullPointerException.class, () -> GenesisBlockFactory.create(null));
    }

    @Test
    @DisplayName("blank chainId throws IllegalArgumentException")
    void blankChainIdThrows() {
        assertThrows(IllegalArgumentException.class, () -> GenesisBlockFactory.create("   "));
    }

    @Test
    @DisplayName("genesis block header version is 1")
    void genesisHeaderVersion() {
        Block genesis = GenesisBlockFactory.create("test");
        assertEquals(1, genesis.getHeader().version());
    }
}
