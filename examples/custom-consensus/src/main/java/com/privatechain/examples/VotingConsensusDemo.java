package com.privatechain.examples;

import com.privatechain.core.builder.BlockchainConfig;
import com.privatechain.core.builder.BlockchainNode;
import com.privatechain.core.exception.BlockValidationException;
import com.privatechain.core.model.Block;
import com.privatechain.core.model.Transaction;
import com.privatechain.storage.memory.InMemoryStorage;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Demonstrates the {@link VotingConsensusEngine} — a user-defined {@link com.privatechain.core.spi.ConsensusEngine}
 * implementation that requires a strict majority of registered validators to approve
 * each block before it can be added to the chain.
 *
 * <p>This example verifies the following library guarantees (AC-08, FR-CONS-06):</p>
 * <ul>
 *   <li>A custom consensus engine is called for every {@code addBlock()} invocation.</li>
 *   <li>A custom engine is injectable via {@link BlockchainConfig#builder()} without
 *       modifying library source.</li>
 *   <li>Block acceptance is correctly gated on vote count reaching the quorum threshold.</li>
 *   <li>A block that does not meet quorum is rejected with {@link BlockValidationException}.</li>
 * </ul>
 *
 * <h2>Run with Maven</h2>
 * <pre>{@code
 * mvn exec:java -pl examples/custom-consensus
 * }</pre>
 *
 * @see VotingConsensusEngine
 * @since 1.0.0
 */
public final class VotingConsensusDemo {

    /**
     * Utility class — no instances.
     */
    private VotingConsensusDemo() {
    }

    /**
     * Runs the demo.
     *
     * @param args ignored
     */
    public static void main(String[] args) {

        // ════════════════════════════════════════════════════════════════════
        // PART 1 — Three-validator majority scenario (2-of-3 required)
        // ════════════════════════════════════════════════════════════════════
        section("1 | Three-validator engine (quorum = 2-of-3)");

        List<String> validators = List.of("validator-A", "validator-B", "validator-C");
        VotingConsensusEngine engine = new VotingConsensusEngine(validators);

        System.out.println("Registered validators: " + engine.getValidators());
        System.out.println("Quorum required: " + engine.getQuorum());

        // Build node with the custom engine — demonstrates FR-CONS-06, AC-08
        BlockchainNode node = BlockchainConfig.builder()
            .consensusEngine(engine)                // inject custom engine
            .storage(new InMemoryStorage())
            .chainId("voting-demo")
            .build();
        node.start();

        System.out.println("Node started — genesis accepted without votes (by design)");
        System.out.println("Chain height: " + node.status().chainHeight());
        System.out.println("Consensus engine: " + node.status().consensusEngine()); // "VotingConsensus"

        // Submit a demo transaction
        Transaction tx = makeTransaction("sender-addr", "receiver-addr", "100.00");
        node.submitTransaction(tx);

        // mineBlock() auto-casts votes from all 3 validators (demo mode)
        // In a real distributed setup, each validator node would call castVote()
        // independently after verifying the block's content.
        List<Transaction> pending = node.getMempool().getTopN(10);
        Block candidate = engine.mineBlock(pending, node.getChain().getLatestBlock());

        System.out.printf("%nCandidate block: #%d (hash prefix: %s...)%n",
            candidate.getIndex(), candidate.getHash().substring(0, 12));
        System.out.printf("Votes cast: %d/%d (quorum=%d)%n",
            engine.getVoteCount(candidate.getHash()),
            validators.size(),
            engine.getQuorum());

        // addBlock() internally calls engine.validateBlock() — which checks vote count
        node.getChain().addBlock(candidate);
        System.out.println("Block #" + candidate.getIndex() + " accepted with full quorum ✓");
        System.out.println("Chain valid: " + node.getChain().isChainValid());
        System.out.println("Chain height: " + node.status().chainHeight());

        node.stop();

        // ════════════════════════════════════════════════════════════════════
        // PART 2 — Quorum failure scenario (only 1-of-5 votes cast)
        // ════════════════════════════════════════════════════════════════════
        section("2 | Quorum failure scenario (1-of-5 votes cast, quorum=3)");

        List<String> fiveValidators =
            List.of("v1", "v2", "v3", "v4", "v5");
        VotingConsensusEngine strictEngine = new VotingConsensusEngine(fiveValidators);

        System.out.println("Registered validators: " + strictEngine.getValidators());
        System.out.println("Quorum required: " + strictEngine.getQuorum());

        BlockchainNode strictNode = BlockchainConfig.builder()
            .consensusEngine(strictEngine)
            .storage(new InMemoryStorage())
            .chainId("strict-voting-demo")
            .build();
        strictNode.start();

        // Build a candidate block without auto-voting (use the ConsensusSupport directly)
        // We simulate a proposer creating a block:
        Block failingCandidate = com.privatechain.consensus.ConsensusSupport.buildBlock(
            strictNode.getChain().getLatestBlock(), // previousBlock = genesis
            List.of(),                              // no transactions
            0, 0L, null,                            // bits, nonce, minerAddress
            Instant.now());

        // Only ONE of five validators votes (below quorum of 3)
        strictEngine.castVote(failingCandidate.getHash(), "v1");

        System.out.printf("%nCandidate block #%d: votes = %d/%d (quorum=%d)%n",
            failingCandidate.getIndex(),
            strictEngine.getVoteCount(failingCandidate.getHash()),
            fiveValidators.size(),
            strictEngine.getQuorum());

        // Attempt to add the under-voted block — must fail
        try {
            strictNode.getChain().addBlock(failingCandidate);
            System.err.println("ERROR: block should have been rejected!");
        } catch (BlockValidationException e) {
            System.out.println("Block correctly rejected (1 vote < quorum 3): "
                + e.getMessage().substring(0, Math.min(80, e.getMessage().length())) + "...");
        }

        // Now cast enough votes to reach quorum and retry
        strictEngine.castVote(failingCandidate.getHash(), "v2");
        strictEngine.castVote(failingCandidate.getHash(), "v3"); // quorum reached

        System.out.printf("%nAdded votes 'v2' and 'v3'. Votes now: %d/%d%n",
            strictEngine.getVoteCount(failingCandidate.getHash()),
            fiveValidators.size());

        strictNode.getChain().addBlock(failingCandidate);
        System.out.println("Block #" + failingCandidate.getIndex() + " accepted after quorum met ✓");
        System.out.println("Chain valid: " + strictNode.getChain().isChainValid());

        strictNode.stop();

        // ════════════════════════════════════════════════════════════════════
        // PART 3 — Demonstrate engine name in node status (AC-08)
        // ════════════════════════════════════════════════════════════════════
        section("3 | Engine name visible in node status (AC-08)");

        VotingConsensusEngine namedEngine = new VotingConsensusEngine(List.of("solo-validator"));
        BlockchainNode namedNode = BlockchainConfig.builder()
            .consensusEngine(namedEngine)
            .storage(new InMemoryStorage())
            .build();
        namedNode.start();

        BlockchainNode.NodeStatus status = namedNode.status();
        System.out.println("NodeStatus.consensusEngine(): " + status.consensusEngine());
        if (!"VotingConsensus".equals(status.consensusEngine())) {
            throw new AssertionError(
                "Expected 'VotingConsensus' but got: " + status.consensusEngine());
        }
        System.out.println("Assertion passed: custom engine name visible in status ✓");

        namedNode.stop();
        System.out.println("\nVotingConsensusDemo complete.");
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Creates a minimal anonymous {@link Transaction} subclass for demo use.
     *
     * @param sender   sender address string
     * @param receiver receiver address string
     * @param amount   decimal amount string
     * @return an unsigned transaction instance
     */
    private static Transaction makeTransaction(String sender, String receiver, String amount) {
        return new Transaction(
            UUID.randomUUID(),
            sender,
            receiver,
            new BigDecimal(amount),
            Instant.now(),
            Map.of()) {
            // Minimal anonymous subclass — no extra fields needed for the demo
        };
    }

    /**
     * Prints a section separator to stdout.
     *
     * @param title the section heading
     */
    private static void section(String title) {
        System.out.println();
        System.out.println("══════════════════════════════════════════");
        System.out.println("  " + title);
        System.out.println("══════════════════════════════════════════");
    }
}
