package com.privatechain.core;

import com.privatechain.core.builder.Blockchain;
import com.privatechain.core.builder.BlockchainConfig;
import com.privatechain.core.builder.BlockchainNode;
import com.privatechain.core.builder.GenesisBlockFactory;
import com.privatechain.core.event.BlockchainEvent;
import com.privatechain.core.event.BlockchainEventBus;
import com.privatechain.core.exception.BlockValidationException;
import com.privatechain.core.model.Block;
import com.privatechain.core.model.BlockHeader;
import com.privatechain.core.spi.ConsensusEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Blockchain} covering block appending, chain validation,
 * and event publication.
 */
@DisplayName("Blockchain")
class BlockchainTest {

    private BlockchainEventBus eventBus;
    private Blockchain blockchain;
    private Block genesis;

    @BeforeEach
    void setUp() {
        eventBus = new BlockchainEventBus();
        // Use buildConfig() to get the raw config object for wiring Blockchain directly
        BlockchainConfig config = BlockchainConfig.builder().buildConfig();
        blockchain = new Blockchain(config.getConsensusEngine(), config.getStorage(), eventBus);

        genesis = GenesisBlockFactory.create("test-chain");
        blockchain.addBlock(genesis);
    }

    // ─── Genesis block ────────────────────────────────────────────────────────

    @Test
    @DisplayName("chain starts with genesis block at index 0")
    void genesisBlockIsAtIndexZero() {
        assertEquals(1, blockchain.size());
        Block stored = blockchain.getBlock(0);
        assertEquals(0, stored.getIndex());
        assertEquals(Block.GENESIS_PREVIOUS_HASH, stored.getPreviousHash());
    }

    @Test
    @DisplayName("getLatestBlock returns genesis when only genesis exists")
    void latestBlockIsGenesis() {
        Block latest = blockchain.getLatestBlock();
        assertEquals(genesis.getHash(), latest.getHash());
    }

    // ─── addBlock ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("addBlock increments chain size")
    void addBlockIncreasesSize() {
        Block second = buildNextBlock(genesis);
        blockchain.addBlock(second);
        assertEquals(2, blockchain.size());
    }

    @Test
    @DisplayName("addBlock with wrong previousHash throws BlockValidationException")
    void wrongPreviousHashThrows() {
        Block bad = Block.builder()
            .index(1)
            .previousHash("wrong".repeat(12) + "0000")
            .header(BlockHeader.builder().merkleRoot("a".repeat(64)).build())
            .build();

        assertThrows(BlockValidationException.class, () -> blockchain.addBlock(bad));
    }

    @Test
    @DisplayName("addBlock with wrong index throws BlockValidationException")
    void wrongIndexThrows() {
        Block bad = Block.builder()
            .index(99)   // should be 1
            .previousHash(genesis.getHash())
            .header(BlockHeader.builder().merkleRoot("a".repeat(64)).build())
            .build();

        assertThrows(BlockValidationException.class, () -> blockchain.addBlock(bad));
    }

    @Test
    @DisplayName("addBlock with tampered hash throws BlockValidationException")
    void tamperedHashThrows() {
        // Construct a block with a bad stored hash
        Block tampered = new Block(1, BlockHeader.builder().merkleRoot("a".repeat(64)).build(),
            genesis.getHash(), "badhash".repeat(9) + "x", List.of());

        assertThrows(BlockValidationException.class, () -> blockchain.addBlock(tampered));
    }

    @Test
    @DisplayName("null block throws NullPointerException")
    void nullBlockThrows() {
        assertThrows(NullPointerException.class, () -> blockchain.addBlock(null));
    }

    // ─── isChainValid ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("isChainValid returns true for a valid chain")
    void validChainPassesValidation() {
        Block second = buildNextBlock(genesis);
        blockchain.addBlock(second);
        assertTrue(blockchain.isChainValid());
    }

    @Test
    @DisplayName("isChainValid returns true for genesis-only chain")
    void singleBlockChainIsValid() {
        assertTrue(blockchain.isChainValid());
    }

    // ─── Event publication ────────────────────────────────────────────────────

    @Test
    @DisplayName("addBlock publishes BlockAddedEvent")
    void addBlockPublishesEvent() throws InterruptedException {
        List<BlockchainEvent> received = new CopyOnWriteArrayList<>();
        eventBus.register(received::add);

        Block second = buildNextBlock(genesis);
        blockchain.addBlock(second);

        // Give the async event bus time to deliver
        Thread.sleep(100);

        // genesis was added before we registered, so we expect 1 event for second
        assertFalse(received.isEmpty());
        assertNotNull(received.get(0));
        assertInstanceOf(BlockchainEvent.BlockAddedEvent.class, received.get(0));
    }

    // ─── BlockchainNode integration ───────────────────────────────────────────

    @Test
    @DisplayName("BlockchainNode.start() creates genesis and starts cleanly")
    void nodeStartCreatesGenesis() {
        BlockchainNode node = BlockchainConfig.builder()
            .chainId("node-test-chain")
            .build()
            .start();

        assertEquals(1, node.getChain().size(), "chain must contain genesis block after start");
        assertNotNull(node.status());
        assertEquals(1, node.status().chainHeight());

        node.stop();
    }

    @Test
    @DisplayName("BlockchainNode.status() reflects consensus engine name")
    void statusShowsEngineName() {
        BlockchainNode node = BlockchainConfig.builder().build().start();

        assertEquals("NoOp", node.status().consensusEngine());

        node.stop();
    }

    @Test
    @DisplayName("start() twice throws IllegalStateException")
    void doubleStartThrows() {
        BlockchainNode node = BlockchainConfig.builder().build().start();
        assertThrows(IllegalStateException.class, node::start);
        node.stop();
    }

    // ─── Custom ConsensusEngine ───────────────────────────────────────────────

    @Test
    @DisplayName("custom ConsensusEngine is called for every addBlock")
    void customConsensusEngineIsCalled() {
        List<Block> validated = new ArrayList<>();

        ConsensusEngine trackingEngine = new ConsensusEngine() {
            @Override
            public boolean validateBlock(Block block, Blockchain chain) {
                validated.add(block);
                return true;
            }

            @Override
            public Block mineBlock(List<com.privatechain.core.model.Transaction> transactions,
                                   Block previousBlock) {
                return buildNextBlock(previousBlock);
            }

            @Override
            public String engineName() {
                return "TrackingEngine";
            }
        };

        BlockchainNode node = BlockchainConfig.builder()
            .consensusEngine(trackingEngine)
            .build()
            .start();

        // genesis is at index 0; add a second block
        Block second = buildNextBlock(node.getChain().getLatestBlock());
        node.getChain().addBlock(second);

        // Both genesis (if validated) and second should have been passed to the engine
        // The no-op engine doesn't call ours for genesis, but our custom one does for second
        assertTrue(validated.contains(second), "custom engine must have been called for second block");

        node.stop();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Block buildNextBlock(Block previous) {
        BlockHeader header = BlockHeader.builder()
            .nonce(1L)
            .merkleRoot("b".repeat(64))
            .build();
        return Block.builder()
            .index(previous.getIndex() + 1)
            .previousHash(previous.getHash())
            .transactions(List.of())
            .header(header)
            .build();
    }
}
