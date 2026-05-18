package com.privatechain.consensus.poa;

import com.privatechain.consensus.ConsensusEngineContractTest;
import com.privatechain.core.builder.Blockchain;
import com.privatechain.core.model.Block;
import com.privatechain.core.spi.ConsensusEngine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ProofOfAuthorityEngine}.
 */
@DisplayName("ProofOfAuthorityEngine")
class ProofOfAuthorityEngineTest extends ConsensusEngineContractTest {

    @Override
    protected ConsensusEngine createEngine() {
        return new ProofOfAuthorityEngine(Set.of("node-a", "node-b", "node-c"));
    }

    @Test
    @DisplayName("mineBlock selects the lexicographically first authorized miner")
    void mineBlockSelectsDeterministicMiner() {
        ProofOfAuthorityEngine engine = new ProofOfAuthorityEngine(Set.of("node-c", "node-a", "node-b"));
        Blockchain chain = createChain(engine);
        Block mined = engine.mineBlock(List.of(), chain.getLatestBlock());

        assertEquals("node-a", mined.getMinerAddress(),
            "PoA mining should deterministically select the first authorized address");
        assertTrue(engine.validateBlock(mined, chain), "The mined PoA block should validate");
    }

    @Test
    @DisplayName("unauthorized miner is rejected")
    void unauthorizedMinerIsRejected() {
        ProofOfAuthorityEngine engine = new ProofOfAuthorityEngine(Set.of("node-a", "node-b"));
        Blockchain chain = createChain(engine);
        Block mined = engine.mineBlock(List.of(), chain.getLatestBlock());
        Block forged = new Block(
            mined.getIndex(),
            mined.getHeader(),
            mined.getPreviousHash(),
            Block.computeHash(mined.getIndex(), mined.getPreviousHash(), mined.getHeader(), "intruder"),
            mined.getTransactions(),
            "intruder");

        assertFalse(engine.validateBlock(forged, chain),
            "A miner outside the authorization set must be rejected");
    }
}

