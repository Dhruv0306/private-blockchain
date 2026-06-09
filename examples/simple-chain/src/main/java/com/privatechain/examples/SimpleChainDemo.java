package com.privatechain.examples;

import com.privatechain.consensus.pow.ProofOfWorkEngine;
import com.privatechain.core.builder.BlockchainConfig;
import com.privatechain.core.builder.BlockchainNode;
import com.privatechain.core.model.Block;
import com.privatechain.core.model.Transaction;
import com.privatechain.storage.ChainExporter;
import com.privatechain.storage.memory.InMemoryStorage;
import com.privatechain.wallet.Wallet;
import com.privatechain.wallet.WalletManager;

import java.math.BigDecimal;
import java.util.List;

/**
 * Minimal, self-contained demonstration of the private-blockchain library.
 *
 * <p>Shows the full end-to-end flow in order:</p>
 * <ol>
 *   <li>Create and start a {@link BlockchainNode} with PoW consensus (≤10 lines — NFR-UX-02)</li>
 *   <li>Generate two wallets using {@link WalletManager}</li>
 *   <li>Build, sign, and submit three {@link MoneyTransferTransaction} instances</li>
 *   <li>Mine a block containing those transactions</li>
 *   <li>Validate chain integrity via {@link com.privatechain.core.builder.Blockchain#isChainValid()}</li>
 *   <li>Query {@link BlockchainNode#status()}</li>
 *   <li>Export and round-trip the chain via {@link ChainExporter#toJson} (FR-SER-02)</li>
 *   <li>Export the chain as CSV via {@link ChainExporter#toCsv} (FR-SER-03)</li>
 *   <li>Compute wallet balance</li>
 *   <li>Stop the node cleanly</li>
 * </ol>
 *
 * <h2>Run with Maven</h2>
 * <pre>{@code
 * mvn exec:java -pl examples/simple-chain
 * }</pre>
 *
 * <h2>Custom transaction type (AC-09)</h2>
 * <p>{@link MoneyTransferTransaction} is defined in this package and extends
 * {@link Transaction}. Jackson's {@code @JsonTypeInfo} on {@code Transaction} writes
 * the concrete class name into the {@code _type} field of every JSON payload, so
 * {@code MoneyTransferTransaction} survives a full JSON round-trip without any
 * type registration step.</p>
 *
 * @see MoneyTransferTransaction
 * @see ChainExporter
 * @since 1.0.0
 */
public final class SimpleChainDemo {

    /**
     * Utility class — no instances.
     */
    private SimpleChainDemo() {
    }

    /**
     * Runs the demo.
     *
     * @param args ignored
     */
    public static void main(String[] args) {

        // ════════════════════════════════════════════════════════════════════
        // STEP 1 — Build and start the node  (≤10 lines — NFR-UX-02)
        // ════════════════════════════════════════════════════════════════════
        section("1 | Node setup");

        // difficulty=4: 4 leading zero bits required in a valid hash.
        // InMemoryStorage is ephemeral — replace with LevelDBStorage for persistence.
        BlockchainNode node = BlockchainConfig.builder()          // line 1
            .consensusEngine(new ProofOfWorkEngine(4))            // line 2
            .storage(new InMemoryStorage())                       // line 3
            .chainId("simple-chain-demo")                         // line 4
            .build();                                             // line 5
        node.start();                                             // line 6

        System.out.println("Node started — chain height: " + node.status().chainHeight());
        System.out.println("Consensus engine: " + node.status().consensusEngine());

        // ════════════════════════════════════════════════════════════════════
        // STEP 2 — Create wallets
        // ════════════════════════════════════════════════════════════════════
        section("2 | Wallet setup");

        WalletManager wm = new WalletManager();
        Wallet alice = wm.createWallet();
        Wallet bob = wm.createWallet();

        System.out.println("Alice address: " + alice.getAddress());
        System.out.println("Bob   address: " + bob.getAddress());

        // ════════════════════════════════════════════════════════════════════
        // STEP 3 — Build, sign, and submit three transactions
        // ════════════════════════════════════════════════════════════════════
        section("3 | Transaction submission");

        for (int i = 1; i <= 3; i++) {
            // MoneyTransferTransaction extends Transaction — demonstrates FR-TX-05
            MoneyTransferTransaction tx = MoneyTransferTransaction.of(
                alice.getAddress(),
                bob.getAddress(),
                BigDecimal.valueOf(10 * i),
                "USD",
                "invoice-00" + i);

            // sign() computes ECDSA over id|sender|receiver|amount|timestamp|currency
            alice.sign(tx);

            // submitTransaction() runs the configured validator chain (none here),
            // then adds to the mempool and publishes TransactionSubmittedEvent
            node.submitTransaction(tx);

            System.out.printf("  TX #%d submitted: %s (amount=%.2f USD)%n",
                i, tx.getId(), tx.getAmount());
        }

        System.out.println("Mempool size: " + node.getMempool().size());

        // ════════════════════════════════════════════════════════════════════
        // STEP 4 — Mine a block
        // ════════════════════════════════════════════════════════════════════
        section("4 | Mining");

        System.out.println("Mining block with difficulty=4 (may take a moment)...");

        // Select up to 10 highest-priority transactions from the mempool
        List<Transaction> selected = node.getMempool().getTopN(10);

        // mineBlock() blocks until a valid nonce is found (PoW)
        Block candidate = node.getChain()
            .getConsensusEngine()
            .mineBlock(selected, node.getChain().getLatestBlock());

        // addBlock() validates hash integrity, chain linkage, and consensus,
        // then persists and publishes BlockAddedEvent (which removes confirmed txs from mempool)
        node.getChain().addBlock(candidate);

        System.out.println("Block #" + candidate.getIndex() + " mined");
        System.out.println("  Hash:        " + candidate.getHash());
        System.out.println("  PrevHash:    " + candidate.getPreviousHash());
        System.out.println("  Tx count:    " + candidate.getTransactions().size());
        System.out.println("Mempool size after block: " + node.getMempool().size());

        // ════════════════════════════════════════════════════════════════════
        // STEP 5 — Validate chain integrity
        // ════════════════════════════════════════════════════════════════════
        section("5 | Chain validation");

        boolean valid = node.getChain().isChainValid();
        System.out.println("Chain valid: " + valid);
        if (!valid) {
            throw new AssertionError("Chain integrity check failed! This should not happen.");
        }

        // ════════════════════════════════════════════════════════════════════
        // STEP 6 — Node status
        // ════════════════════════════════════════════════════════════════════
        section("6 | Node status");

        BlockchainNode.NodeStatus status = node.status();
        System.out.printf("  Chain height:   %d%n", status.chainHeight());
        System.out.printf("  Mempool size:   %d%n", status.mempoolSize());
        System.out.printf("  Peer count:     %d%n", status.peerCount());
        System.out.printf("  Last block:     %s%n", status.lastBlockTime());
        System.out.printf("  Consensus:      %s%n", status.consensusEngine());

        // ════════════════════════════════════════════════════════════════════
        // STEP 7 — JSON export and round-trip (FR-SER-02)
        // ════════════════════════════════════════════════════════════════════
        section("7 | JSON export (FR-SER-02)");

        String json = ChainExporter.toJson(node.getChain());
        System.out.println("Chain JSON (first 200 chars): "
            + json.substring(0, Math.min(200, json.length())) + "...");

        // Restore into a fresh storage and verify block count matches
        InMemoryStorage freshStorage = new InMemoryStorage();
        ChainExporter.fromJson(json, freshStorage);

        System.out.println("JSON round-trip: OK");
        System.out.printf("  Original blocks: %d | Restored blocks: %d%n",
            node.getChain().size(), freshStorage.chainHeight());
        if (freshStorage.chainHeight() != node.getChain().size()) {
            throw new AssertionError("JSON round-trip chain height mismatch!");
        }

        // ════════════════════════════════════════════════════════════════════
        // STEP 8 — CSV export (FR-SER-03)
        // ════════════════════════════════════════════════════════════════════
        section("8 | CSV export (FR-SER-03)");

        String csv = ChainExporter.toCsv(node.getChain());
        System.out.println("Chain CSV output:");
        System.out.println(csv);

        // ════════════════════════════════════════════════════════════════════
        // STEP 9 — Wallet balance query
        // ════════════════════════════════════════════════════════════════════
        section("9 | Wallet balances");

        // Balance = sum of incoming txs - sum of outgoing txs across all confirmed blocks
        BigDecimal bobBalance = bob.getBalance(node.getChain());
        BigDecimal aliceBalance = alice.getBalance(node.getChain());
        System.out.printf("  Alice: %s USD%n", aliceBalance.toPlainString());
        System.out.printf("  Bob:   %s USD%n", bobBalance.toPlainString());

        // Expected: Bob receives 10 + 20 + 30 = 60 USD; Alice sends 60 USD
        if (bobBalance.compareTo(new BigDecimal("60")) != 0) {
            throw new AssertionError("Unexpected Bob balance: " + bobBalance + " (expected 60)");
        }

        // ════════════════════════════════════════════════════════════════════
        // STEP 10 — Stop
        // ════════════════════════════════════════════════════════════════════
        section("10 | Shutdown");

        node.stop();
        System.out.println("Node stopped cleanly. SimpleChainDemo complete.");
    }

    /**
     * Prints a section header separator to stdout.
     *
     * @param title human-readable section title
     */
    private static void section(String title) {
        System.out.println();
        System.out.println("══════════════════════════════════════════");
        System.out.println("  " + title);
        System.out.println("══════════════════════════════════════════");
    }
}
