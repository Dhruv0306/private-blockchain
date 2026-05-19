package com.privatechain.consensus.roundrobin;

import com.privatechain.consensus.ConsensusEngineContractTest;
import com.privatechain.core.builder.Blockchain;
import com.privatechain.core.model.Block;
import com.privatechain.core.spi.ConsensusEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RoundRobinEngine}.
 */
@DisplayName("RoundRobinEngine")
class RoundRobinEngineTest extends ConsensusEngineContractTest {

    @Override
    protected ConsensusEngine createEngine() {
        return new RoundRobinEngine(List.of("node-a", "node-b", "node-c"));
    }

    @Test
    @DisplayName("mineBlock selects the peer for the next slot")
    void mineBlockSelectsNextSlotPeer() {
        RoundRobinEngine engine = new RoundRobinEngine(List.of("node-a", "node-b", "node-c"));
        Blockchain chain = createChain(engine);
        Block mined = engine.mineBlock(List.of(), chain.getLatestBlock());

        assertEquals("node-b", mined.getMinerAddress(),
            "For block index 1, the engine should choose slot 1 from the peer list");
        assertTrue(engine.validateBlock(mined, chain), "The mined round-robin block should validate");
    }

    @Test
    @DisplayName("block from wrong slot is rejected")
    void wrongSlotMinerIsRejected() {
        RoundRobinEngine engine = new RoundRobinEngine(List.of("node-a", "node-b", "node-c"));
        Blockchain chain = createChain(engine);
        Block mined = engine.mineBlock(List.of(), chain.getLatestBlock());
        Block forged = new Block(
            mined.getIndex(),
            mined.getHeader(),
            mined.getPreviousHash(),
            mined.getHash(),
            mined.getTransactions(),
            "node-a");

        assertFalse(engine.validateBlock(forged, chain),
            "A block claimed by the wrong slot owner must be rejected");
    }
}

