package com.privatechain.consensus.pbft;

import com.privatechain.consensus.ConsensusSupport;
import com.privatechain.core.builder.Blockchain;
import com.privatechain.core.exception.ConsensusException;
import com.privatechain.core.model.Block;
import com.privatechain.core.model.Transaction;
import com.privatechain.core.spi.ConsensusEngine;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Practical Byzantine Fault Tolerance consensus engine.
 *
 * <p>This implementation keeps the PBFT contract lightweight and deterministic while
 * still exposing the same public API shape that a network-backed implementation would
 * use. The engine requires a configurable quorum and a stable, ordered validator set.
 * It validates that the block was produced by an authorized validator and that the
 * block header carries the configured quorum marker.</p>
 *
 * @since 1.0.0
 */
public final class PBFTEngine implements ConsensusEngine {

    private final List<String> validators;
    private final int quorumSize;

    /**
     * Creates a PBFT engine with an automatically derived quorum size.
     *
     * <p>The quorum follows the common {@code 2f + 1} rule for a validator set of
     * size {@code n}, which evaluates to {@code floor(2n/3) + 1}. The value is at
     * least 1 so that a single-node test chain remains usable.</p>
     *
     * @param validators ordered validator addresses participating in consensus
     * @throws NullPointerException     if {@code validators} is null
     * @throws IllegalArgumentException if the collection contains blank entries
     */
    public PBFTEngine(Collection<String> validators) {
        this(validators, deriveQuorumSize(validators));
    }

    /**
     * Creates a PBFT engine with an explicit quorum size.
     *
     * @param validators ordered validator addresses participating in consensus
     * @param quorumSize number of distinct validator approvals required for commit
     * @throws NullPointerException     if {@code validators} is null
     * @throws IllegalArgumentException if the validator list contains blanks or the quorum is invalid
     */
    public PBFTEngine(Collection<String> validators, int quorumSize) {
        this.validators = ConsensusSupport.copyAndValidate(validators, "validators", true);
        if (quorumSize < 1) {
            throw new IllegalArgumentException("quorumSize must be >= 1, got: " + quorumSize);
        }
        this.quorumSize = quorumSize;
    }

    /**
     * Creates a PBFT engine with an explicit quorum size.
     *
     * @param quorumSize number of validator approvals required for commit
     * @param validators ordered validator addresses participating in consensus
     * @throws NullPointerException     if {@code validators} is null
     * @throws IllegalArgumentException if the validator list contains blanks or the quorum is invalid
     */
    public PBFTEngine(int quorumSize, Collection<String> validators) {
        this(validators, quorumSize);
    }

    /**
     * Derives a conservative quorum size from the validator set size.
     *
     * @param validators validator collection
     * @return quorum size in the usual {@code 2f + 1} form
     * @throws NullPointerException if {@code validators} is null
     */
    private static int deriveQuorumSize(Collection<String> validators) {
        Objects.requireNonNull(validators, "validators must not be null");
        int size = validators.size();
        return Math.max(1, (size * 2) / 3 + 1);
    }

    /**
     * Returns the configured validators.
     *
     * @return immutable list of validator addresses
     */
    public List<String> getValidators() {
        return List.copyOf(validators);
    }

    /**
     * Returns the configured quorum size.
     *
     * @return quorum size in validators
     */
    public int getQuorumSize() {
        return quorumSize;
    }

    /**
     * Validates whether the candidate block satisfies the PBFT acceptance rules.
     *
     * @param block the candidate block to validate
     * @param chain the current blockchain state
     * @return {@code true} if the block is valid for this engine
     * @throws NullPointerException if {@code block} or {@code chain} is null
     * @throws ConsensusException   if the configured quorum cannot be satisfied
     */
    @Override
    public boolean validateBlock(Block block, Blockchain chain) {
        Objects.requireNonNull(block, "block must not be null");
        Objects.requireNonNull(chain, "chain must not be null");

        if (ConsensusSupport.isGenesis(block)) {
            return true;
        }
        if (validators.size() < quorumSize) {
            throw new ConsensusException(
                "PBFT quorum cannot be satisfied: validators=" + validators.size()
                    + ", quorumSize=" + quorumSize);
        }

        return ConsensusSupport.hasConsistentIntegrity(block)
            && block.getHeader().bits() == quorumSize
            && block.getHeader().nonce() == 0L
            && block.getMinerAddress() != null
            && validators.contains(block.getMinerAddress());
    }

    /**
     * Produces a new block using the deterministic PBFT leader for the current view.
     *
     * @param transactions  ordered transactions to include in the block
     * @param previousBlock the current chain tip
     * @return a newly produced block
     * @throws NullPointerException if {@code transactions} or {@code previousBlock} is null
     * @throws ConsensusException   if the configured quorum cannot be satisfied
     */
    @Override
    public Block mineBlock(List<Transaction> transactions, Block previousBlock) {
        Objects.requireNonNull(transactions, "transactions must not be null");
        Objects.requireNonNull(previousBlock, "previousBlock must not be null");

        if (validators.size() < quorumSize) {
            throw new ConsensusException(
                "PBFT quorum cannot be satisfied: validators=" + validators.size()
                    + ", quorumSize=" + quorumSize);
        }

        String leader = validators.get(0);
        return ConsensusSupport.buildBlock(previousBlock, transactions, quorumSize, 0L, leader, Instant.now());
    }

    /**
     * Returns the stable name used to identify this engine in logs and status output.
     *
     * @return the engine name
     */
    @Override
    public String engineName() {
        return "PBFT";
    }
}

