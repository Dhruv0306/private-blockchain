package com.privatechain.consensus.roundrobin;

import com.privatechain.consensus.ConsensusSupport;
import com.privatechain.core.builder.Blockchain;
import com.privatechain.core.exception.ConsensusException;
import com.privatechain.core.model.Block;
import com.privatechain.core.model.Transaction;
import com.privatechain.core.spi.ConsensusEngine;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Slot-based consensus engine.
 *
 * <p>This implementation rotates block production through a fixed, ordered peer list.
 * The miner for a given block is selected deterministically by applying the block
 * index modulo the configured peer count, which makes the engine predictable and
 * especially useful for tests, demos, and scripted integration scenarios.</p>
 *
 * @since 1.0.0
 */
public final class RoundRobinEngine implements ConsensusEngine {

    private final List<String> peers;

    /**
     * Creates a round-robin engine with the supplied peer order.
     *
     * @param peers ordered peer list used for slot rotation
     * @throws NullPointerException     if {@code peers} is null
     * @throws IllegalArgumentException if the collection contains blank entries
     */
    public RoundRobinEngine(List<String> peers) {
        this.peers = ConsensusSupport.copyAndValidate(peers, "peers", false);
    }

    /**
     * Returns the configured peer order.
     *
     * @return immutable ordered peer list
     */
    public List<String> getPeers() {
        return List.copyOf(peers);
    }

    /**
     * Validates whether the candidate block was produced by the peer assigned to
     * the current slot.
     *
     * @param block the candidate block to validate
     * @param chain the current blockchain state
     * @return {@code true} if the block is valid for this engine
     * @throws NullPointerException if {@code block} or {@code chain} is null
     * @throws ConsensusException   if the peer list is empty
     */
    @Override
    public boolean validateBlock(Block block, Blockchain chain) {
        Objects.requireNonNull(block, "block must not be null");
        Objects.requireNonNull(chain, "chain must not be null");

        if (ConsensusSupport.isGenesis(block)) {
            return true;
        }
        if (peers.isEmpty()) {
            throw new ConsensusException("Round-robin consensus requires at least one peer");
        }

        int expectedIndex = block.getIndex() % peers.size();
        String expectedMiner = peers.get(expectedIndex);
        return ConsensusSupport.hasConsistentIntegrity(block)
            && block.getHeader().bits() == 0
            && block.getHeader().nonce() == 0L
            && expectedMiner.equals(block.getMinerAddress());
    }

    /**
     * Produces a new block for the next round-robin slot.
     *
     * @param transactions  ordered transactions to include in the block
     * @param previousBlock the current chain tip
     * @return a newly produced block
     * @throws NullPointerException if {@code transactions} or {@code previousBlock} is null
     * @throws ConsensusException   if the peer list is empty
     */
    @Override
    public Block mineBlock(List<Transaction> transactions, Block previousBlock) {
        Objects.requireNonNull(transactions, "transactions must not be null");
        Objects.requireNonNull(previousBlock, "previousBlock must not be null");

        if (peers.isEmpty()) {
            throw new ConsensusException("Round-robin consensus requires at least one peer");
        }

        int nextIndex = previousBlock.getIndex() + 1;
        String minerAddress = peers.get(nextIndex % peers.size());
        return ConsensusSupport.buildBlock(previousBlock, transactions, 0, 0L, minerAddress, Instant.now());
    }

    /**
     * Returns the stable name used to identify this engine in logs and status output.
     *
     * @return the engine name
     */
    @Override
    public String engineName() {
        return "RoundRobin";
    }
}

