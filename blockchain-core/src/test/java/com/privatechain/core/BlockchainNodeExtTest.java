package com.privatechain.core;

import com.privatechain.core.builder.BlockchainConfig;
import com.privatechain.core.builder.BlockchainNode;
import com.privatechain.core.exception.TransactionValidationException;
import com.privatechain.core.model.Transaction;
import com.privatechain.core.spi.ValidationResult;
import com.privatechain.core.spi.ValidationResult.ValidationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Extended unit tests for {@link BlockchainNode} and {@link BlockchainConfig} covering
 * transaction submission, validation chain wiring, config builder edge cases,
 * and lifecycle error paths.
 */
@DisplayName("BlockchainNode — extended")
class BlockchainNodeExtTest {

    // ─── Concrete transaction for testing ─────────────────────────────────────

    @Test
    @DisplayName("stop() before start() throws IllegalStateException")
    void stopBeforeStartThrows() {
        BlockchainNode node = BlockchainConfig.builder().build();
        assertThrows(IllegalStateException.class, node::stop,
            "stop() must throw if start() was never called");
    }

    // ─── Lifecycle error paths ────────────────────────────────────────────────

    @Test
    @DisplayName("status() before start() throws IllegalStateException")
    void statusBeforeStartThrows() {
        BlockchainNode node = BlockchainConfig.builder().build();
        assertThrows(IllegalStateException.class, node::status,
            "status() must throw if node is not started");
    }

    @Test
    @DisplayName("submitTransaction() before start() throws IllegalStateException")
    void submitBeforeStartThrows() {
        BlockchainNode node = BlockchainConfig.builder().build();
        SimpleTransaction tx = new SimpleTransaction("alice", "bob", BigDecimal.ONE);
        assertThrows(IllegalStateException.class, () -> node.submitTransaction(tx),
            "submitTransaction() must throw if node is not started");
    }

    @Test
    @DisplayName("submitTransaction() null throws NullPointerException")
    void submitNullTransactionThrows() {
        BlockchainNode node = BlockchainConfig.builder().build().start();
        assertThrows(NullPointerException.class, () -> node.submitTransaction(null));
        node.stop();
    }

    @Test
    @DisplayName("submitTransaction passes when no validators are configured")
    void submitWithNoValidatorsSucceeds() {
        // Zero validators → auto-accept all transactions
        BlockchainNode node = BlockchainConfig.builder().build().start();

        SimpleTransaction tx = new SimpleTransaction("alice", "bob", BigDecimal.TEN);
        // Must not throw — no validators to reject it
        node.submitTransaction(tx);

        node.stop();
    }

    // ─── Transaction validation chain ─────────────────────────────────────────

    @Test
    @DisplayName("submitTransaction rejected by first validator throws TransactionValidationException")
    void submitRejectedByFirstValidator() {
        BlockchainNode node = BlockchainConfig.builder()
            .transactionValidator((tx, chain) ->
                ValidationResult.failure(ValidationStatus.INVALID_SIGNATURE,
                    "test rejection"))
            .build()
            .start();

        SimpleTransaction tx = new SimpleTransaction("alice", "bob", BigDecimal.ONE);
        TransactionValidationException ex = assertThrows(
            TransactionValidationException.class,
            () -> node.submitTransaction(tx));

        assertEquals(ValidationStatus.INVALID_SIGNATURE,
            ex.getValidationResult().getStatus());
        assertEquals("test rejection",
            ex.getValidationResult().getErrors().get(0));

        node.stop();
    }

    @Test
    @DisplayName("submitTransaction passes first validator but fails second — exception carries correct status")
    void submitFailsSecondValidator() {
        BlockchainNode node = BlockchainConfig.builder()
            // first validator: always passes
            .transactionValidator((tx, chain) -> ValidationResult.success())
            // second validator: always rejects
            .transactionValidator((tx, chain) ->
                ValidationResult.failure(ValidationStatus.INSUFFICIENT_FUNDS,
                    "balance too low"))
            .build()
            .start();

        SimpleTransaction tx = new SimpleTransaction("alice", "bob", BigDecimal.ONE);
        TransactionValidationException ex = assertThrows(
            TransactionValidationException.class,
            () -> node.submitTransaction(tx));

        assertEquals(ValidationStatus.INSUFFICIENT_FUNDS,
            ex.getValidationResult().getStatus());

        node.stop();
    }

    @Test
    @DisplayName("submitTransaction passes all validators and succeeds")
    void submitPassesAllValidators() {
        BlockchainNode node = BlockchainConfig.builder()
            .transactionValidator((tx, chain) -> ValidationResult.success())
            .transactionValidator((tx, chain) -> ValidationResult.success())
            .transactionValidator((tx, chain) -> ValidationResult.success())
            .build()
            .start();

        SimpleTransaction tx = new SimpleTransaction("alice", "bob", BigDecimal.TEN);
        // Must not throw
        node.submitTransaction(tx);

        node.stop();
    }

    @Test
    @DisplayName("status() after start reflects chain height of 1 (genesis)")
    void statusChainHeight() {
        BlockchainNode node = BlockchainConfig.builder().build().start();

        BlockchainNode.NodeStatus status = node.status();
        assertEquals(1, status.chainHeight(), "genesis block must be counted");
        assertEquals(0, status.mempoolSize(), "mempool is empty before Milestone 5");
        assertEquals(0, status.peerCount(), "peer count is 0 before Milestone 7");
        assertNotNull(status.lastBlockTime(), "lastBlockTime must reflect genesis block header timestamp");
        assertEquals("NoOp", status.consensusEngine());

        node.stop();
    }

    // ─── NodeStatus ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("status() consensusEngine reflects custom engine name")
    void statusCustomEngineName() {
        BlockchainNode node = BlockchainConfig.builder()
            .consensusEngine(new com.privatechain.core.spi.ConsensusEngine() {
                @Override
                public boolean validateBlock(
                    com.privatechain.core.model.Block b,
                    com.privatechain.core.builder.Blockchain c) {
                    return true;
                }

                @Override
                public com.privatechain.core.model.Block mineBlock(
                    java.util.List<Transaction> txs,
                    com.privatechain.core.model.Block prev) {
                    return prev;
                }

                @Override
                public String engineName() {
                    return "CustomTestEngine";
                }
            })
            .build()
            .start();

        assertEquals("CustomTestEngine", node.status().consensusEngine());
        node.stop();
    }

    @Test
    @DisplayName("networkPort out of range throws IllegalArgumentException")
    void invalidPortThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> BlockchainConfig.builder().networkPort(80));
        assertThrows(IllegalArgumentException.class,
            () -> BlockchainConfig.builder().networkPort(70000));
    }

    // ─── BlockchainConfig builder paths ───────────────────────────────────────

    @Test
    @DisplayName("blockTimeSeconds < 1 throws IllegalArgumentException")
    void invalidBlockTimeThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> BlockchainConfig.builder().blockTimeSeconds(0));
    }

    @Test
    @DisplayName("difficulty < 1 throws IllegalArgumentException")
    void invalidDifficultyThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> BlockchainConfig.builder().difficulty(0));
    }

    @Test
    @DisplayName("maxPeers < 1 throws IllegalArgumentException")
    void invalidMaxPeersThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> BlockchainConfig.builder().maxPeers(0));
    }

    @Test
    @DisplayName("null chainId throws NullPointerException")
    void nullChainIdThrows() {
        assertThrows(NullPointerException.class,
            () -> BlockchainConfig.builder().chainId(null));
    }

    @Test
    @DisplayName("blank chainId throws IllegalArgumentException")
    void blankChainIdThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> BlockchainConfig.builder().chainId("  "));
    }

    @Test
    @DisplayName("null consensusEngine throws NullPointerException")
    void nullConsensusEngineThrows() {
        assertThrows(NullPointerException.class,
            () -> BlockchainConfig.builder().consensusEngine(null));
    }

    @Test
    @DisplayName("null storage throws NullPointerException")
    void nullStorageThrows() {
        assertThrows(NullPointerException.class,
            () -> BlockchainConfig.builder().storage(null));
    }

    @Test
    @DisplayName("null eventBus throws NullPointerException")
    void nullEventBusThrows() {
        assertThrows(NullPointerException.class,
            () -> BlockchainConfig.builder().eventBus(null));
    }

    @Test
    @DisplayName("null transactionValidator throws NullPointerException")
    void nullValidatorThrows() {
        assertThrows(NullPointerException.class,
            () -> BlockchainConfig.builder().transactionValidator(null));
    }

    @Test
    @DisplayName("null eventListener throws NullPointerException")
    void nullEventListenerThrows() {
        assertThrows(NullPointerException.class,
            () -> BlockchainConfig.builder().eventListener(null));
    }

    @Test
    @DisplayName("null prioritizer throws NullPointerException")
    void nullPrioritizerThrows() {
        assertThrows(NullPointerException.class,
            () -> BlockchainConfig.builder().transactionPrioritizer(null));
    }

    @Test
    @DisplayName("buildConfig() returns BlockchainConfig with all configured values accessible")
    void buildConfigReturnsConfig() {
        BlockchainConfig config = BlockchainConfig.builder()
            .networkPort(9000)
            .blockTimeSeconds(5)
            .difficulty(3)
            .maxPeers(10)
            .chainId("test-chain")
            .buildConfig();

        assertEquals(9000, config.getNetworkPort());
        assertEquals(5, config.getBlockTimeSeconds());
        assertEquals(3, config.getDifficulty());
        assertEquals(10, config.getMaxPeers());
        assertEquals("test-chain", config.getChainId());
        assertNotNull(config.getConsensusEngine());
        assertNotNull(config.getStorage());
        assertNotNull(config.getEventBus());
        assertNotNull(config.getTransactionPrioritizer());
    }

    @Test
    @DisplayName("event listeners registered via builder are wired on build()")
    void eventListenersWiredOnBuild() throws InterruptedException {
        java.util.concurrent.atomic.AtomicInteger eventCount =
            new java.util.concurrent.atomic.AtomicInteger(0);

        BlockchainNode node = BlockchainConfig.builder()
            .chainId("listener-test-chain")
            .eventListener(event -> eventCount.incrementAndGet())
            .build()
            .start();

        // Genesis block addition publishes a BlockAddedEvent
        Thread.sleep(200); // allow async delivery

        assertTrue(eventCount.get() >= 1,
            "pre-registered listener must receive genesis BlockAddedEvent");

        node.stop();
    }

    @Test
    @DisplayName("second start() after resume from storage returns chain size > 1")
    void resumingChainPreservesBlocks() {
        // Build a shared in-memory storage and add genesis + one more block
        com.privatechain.core.spi.BlockchainStorage sharedStorage =
            BlockchainConfig.builder().buildConfig().getStorage();

        // First node: start, add a block, stop
        BlockchainConfig config1 = BlockchainConfig.builder()
            .storage(sharedStorage)
            .chainId("resume-chain")
            .buildConfig();

        BlockchainNode node1 = new BlockchainNode(config1);
        node1.start();

        // Simulate mining block 1
        com.privatechain.core.model.Block genesis = node1.getChain().getLatestBlock();
        com.privatechain.core.model.BlockHeader h = com.privatechain.core.model.BlockHeader
            .builder().nonce(1L).merkleRoot("b".repeat(64)).build();
        com.privatechain.core.model.Block block1 = com.privatechain.core.model.Block.builder()
            .index(1)
            .previousHash(genesis.getHash())
            .transactions(java.util.List.of())
            .header(h)
            .build();
        node1.getChain().addBlock(block1);
        assertEquals(2, node1.getChain().size());
        node1.stop();

        // Second node: reuse same storage → must resume from height 2
        BlockchainConfig config2 = BlockchainConfig.builder()
            .storage(sharedStorage)
            .chainId("resume-chain")
            .buildConfig();
        BlockchainNode node2 = new BlockchainNode(config2);
        node2.start();

        assertEquals(2, node2.getChain().size(),
            "second node must resume at the same chain height");
        node2.stop();
    }

    static final class SimpleTransaction extends Transaction {
        SimpleTransaction(String sender, String receiver, BigDecimal amount) {
            super(UUID.randomUUID(), sender, receiver, amount, Instant.now(), null);
        }
    }
}
