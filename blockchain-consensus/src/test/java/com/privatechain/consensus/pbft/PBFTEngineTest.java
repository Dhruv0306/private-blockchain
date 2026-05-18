package com.privatechain.consensus.pbft;

import com.privatechain.consensus.ConsensusEngineContractTest;
import com.privatechain.core.builder.Blockchain;
import com.privatechain.core.exception.ConsensusException;
import com.privatechain.core.model.Block;
import com.privatechain.core.spi.ConsensusEngine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link PBFTEngine}.
 */
@DisplayName("PBFTEngine")
class PBFTEngineTest extends ConsensusEngineContractTest {

    @Override
    protected ConsensusEngine createEngine() {
        return new PBFTEngine(List.of("node-a", "node-b", "node-c", "node-d"), 3);
    }

    @Test
    @DisplayName("mineBlock encodes the quorum size in the header")
    void mineBlockEncodesQuorumSize() {
        PBFTEngine engine = new PBFTEngine(List.of("node-a", "node-b", "node-c", "node-d"), 3);
        Blockchain chain = createChain(engine);
        Block mined = engine.mineBlock(List.of(), chain.getLatestBlock());

        assertEquals(3, mined.getHeader().bits(),
            "PBFT blocks should retain the configured quorum size in the header marker");
        assertEquals("node-a", mined.getMinerAddress(),
            "The deterministic PBFT leader should be the first validator in the ordered set");
        assertTrue(engine.validateBlock(mined, chain), "The mined PBFT block should validate");
    }

    @Test
    @DisplayName("quorum larger than the validator set fails fast")
    void quorumLargerThanValidatorSetFailsFast() {
        PBFTEngine engine = new PBFTEngine(List.of("node-a", "node-b"), 3);
        Blockchain chain = createChain(engine);

        assertThrows(ConsensusException.class,
            () -> engine.mineBlock(List.of(), chain.getLatestBlock()),
            "PBFT mining must fail when the configured quorum cannot be satisfied");
    }
}

