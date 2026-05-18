package com.privatechain.consensus.pow;

import com.privatechain.consensus.ConsensusEngineContractTest;
import com.privatechain.core.model.Block;
import com.privatechain.core.spi.ConsensusEngine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ProofOfWorkEngine}.
 */
@DisplayName("ProofOfWorkEngine")
class ProofOfWorkEngineTest extends ConsensusEngineContractTest {

    @Override
    protected ConsensusEngine createEngine() {
        return new ProofOfWorkEngine(4);
    }

    @Test
    @DisplayName("mined block records the configured difficulty")
    void minedBlockRecordsConfiguredDifficulty() {
        ProofOfWorkEngine engine = new ProofOfWorkEngine(4);
        Block mined = engine.mineBlock(List.of(), createChain(engine).getLatestBlock());

        assertEquals(4, mined.getHeader().bits(), "PoW block header must store the configured difficulty");
        assertTrue(mined.getHash().startsWith("0"),
            "A valid proof-of-work hash should start with at least one zero hex digit at difficulty 4");
    }
}

