package com.privatechain.consensus;

import com.privatechain.core.builder.Blockchain;
import com.privatechain.core.builder.GenesisBlockFactory;
import com.privatechain.core.event.BlockchainEventBus;
import com.privatechain.core.model.Block;
import com.privatechain.core.model.BlockHeader;
import com.privatechain.core.model.Transaction;
import com.privatechain.core.spi.BlockchainStorage;
import com.privatechain.core.spi.ConsensusEngine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared contract tests for built-in {@link ConsensusEngine} implementations.
 *
 * <p>Each concrete test class supplies an engine instance. The contract then verifies
 * the common lifecycle expected from every engine shipped in this module:</p>
 * <ul>
 *   <li>the deterministic genesis block is accepted,</li>
 *   <li>mining produces a block that validates on the same engine instance, and</li>
 *   <li>tampering with a mined block causes validation to fail.</li>
 * </ul>
 *
 * <p>Keeping the contract in one place ensures the public SPI behaves consistently
 * across all engines while avoiding duplicated test logic in the individual engine
 * test classes.</p>
 *
 * @since 1.0.0
 */
@DisplayName("ConsensusEngine contract")
public abstract class ConsensusEngineContractTest {

    /**
     * Creates the engine under test.
     *
     * @return a concrete consensus engine instance
     */
    protected abstract ConsensusEngine createEngine();

    /**
     * Creates a minimal blockchain containing a deterministic genesis block.
     *
     * <p>The contract test uses a small in-memory storage stub so the engine can be
     * exercised without depending on the storage module.</p>
     *
     * @param engine the engine to inject into the chain
     * @return a blockchain with a preloaded genesis block
     */
    protected final Blockchain createChain(ConsensusEngine engine) {
        BlockchainStorage storage = new TestBlockchainStorage();
        Block genesis = GenesisBlockFactory.create("consensus-contract-chain");
        storage.saveBlock(genesis);
        return new Blockchain(engine, storage, new BlockchainEventBus());
    }

    /**
     * Returns a deterministic empty transaction list used by the contract tests.
     *
     * @return an immutable empty list
     */
    protected final List<Transaction> emptyTransactions() {
        return List.of();
    }

    @Test
    @DisplayName("engine name is non-blank")
    void engineNameIsStable() {
        ConsensusEngine engine = createEngine();

        assertNotNull(engine.engineName(), "engineName() must not return null");
        assertFalse(engine.engineName().isBlank(), "engineName() must not be blank");
    }

    @Test
    @DisplayName("genesis block is accepted")
    void genesisBlockIsAccepted() {
        ConsensusEngine engine = createEngine();
        Blockchain chain = createChain(engine);
        Block genesis = chain.getLatestBlock();

        assertTrue(engine.validateBlock(genesis, chain),
            "Every built-in engine must accept the deterministic genesis block");
    }

    @Test
    @DisplayName("mineBlock creates a valid next block")
    void mineBlockProducesValidBlock() {
        ConsensusEngine engine = createEngine();
        Blockchain chain = createChain(engine);
        Block mined = engine.mineBlock(emptyTransactions(), chain.getLatestBlock());

        assertTrue(engine.validateBlock(mined, chain),
            "A mined block must validate on the same engine instance");
        assertTrue(mined.isHashValid(), "The mined block hash must be internally consistent");
        assertEquals(chain.getLatestBlock().getIndex() + 1, mined.getIndex(),
            "mineBlock() must advance the chain by exactly one block");
    }

    @Test
    @DisplayName("tampered block is rejected")
    void tamperedBlockIsRejected() {
        ConsensusEngine engine = createEngine();
        Blockchain chain = createChain(engine);
        Block mined = engine.mineBlock(emptyTransactions(), chain.getLatestBlock());

        Block tampered = new Block(
            mined.getIndex(),
            BlockHeader.builder()
                .version(mined.getHeader().version())
                .bits(mined.getHeader().bits())
                .nonce(mined.getHeader().nonce() + 1L)
                .merkleRoot(mined.getHeader().merkleRoot())
                .timestamp(mined.getHeader().timestamp())
                .build(),
            mined.getPreviousHash(),
            mined.getHash(),
            mined.getTransactions(),
            mined.getMinerAddress());

        assertFalse(engine.validateBlock(tampered, chain),
            "A block whose contents no longer match its hash must be rejected");
    }

    /**
     * Minimal in-memory storage used only by the consensus contract tests.
     *
     * <p>The consensus module deliberately avoids depending on the storage module so
     * that its tests remain lightweight and self-contained. This nested test stub
     * satisfies the {@link BlockchainStorage} SPI without introducing any extra
     * module dependencies.</p>
     */
    private static final class TestBlockchainStorage implements BlockchainStorage {

        private final Map<Integer, Block> store = new TreeMap<>();

        @Override
        public void saveBlock(Block block) {
            Objects.requireNonNull(block, "block must not be null");
            store.put(block.getIndex(), block);
        }

        @Override
        public Block loadBlock(int index) {
            Block block = store.get(index);
            if (block == null) {
                throw new NoSuchElementException("No block stored at index " + index);
            }
            return block;
        }

        @Override
        public Optional<Block> loadBlockByHash(String hash) {
            Objects.requireNonNull(hash, "hash must not be null");
            return store.values().stream().filter(block -> hash.equals(block.getHash())).findFirst();
        }

        @Override
        public List<Block> loadAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public boolean exists(String hash) {
            return loadBlockByHash(hash).isPresent();
        }

        @Override
        public int chainHeight() {
            return store.size();
        }

        @Override
        public void deleteAll() {
            store.clear();
        }
    }
}

