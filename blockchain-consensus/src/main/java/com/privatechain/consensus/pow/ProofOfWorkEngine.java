package com.privatechain.consensus.pow;

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
 * SHA-256 proof-of-work consensus engine.
 *
 * <p>This engine follows the classic mining model: it repeatedly increments the nonce
 * until the resulting block hash satisfies the configured leading-zero-bit target.
 * Validation is intentionally strict and checks the following invariants:</p>
 * <ul>
 *   <li>the block hash still matches the block contents,</li>
 *   <li>the Merkle root matches the ordered transaction list, and</li>
 *   <li>the hash satisfies the configured proof-of-work prefix target.</li>
 * </ul>
 *
 * <p>The default difficulty matches the repository’s milestone requirements and is
 * suitable for local tests and demos.</p>
 *
 * @since 1.0.0
 */
public final class ProofOfWorkEngine implements ConsensusEngine {

    /**
     * Default difficulty in leading zero bits.
     */
    public static final int DEFAULT_DIFFICULTY = 4;

    private final int difficulty;

    /**
     * Creates a proof-of-work engine using the default difficulty.
     *
     * <p>This is the recommended constructor for tests and examples that only need
     * a working proof-of-work chain with the repository default target.</p>
     */
    public ProofOfWorkEngine() {
        this(DEFAULT_DIFFICULTY);
    }

    /**
     * Creates a proof-of-work engine using the supplied difficulty.
     *
     * @param difficulty required number of leading zero bits in a valid hash
     * @throws IllegalArgumentException if {@code difficulty} is less than 1
     */
    public ProofOfWorkEngine(int difficulty) {
        if (difficulty < 1) {
            throw new IllegalArgumentException("difficulty must be >= 1, got: " + difficulty);
        }
        this.difficulty = difficulty;
    }

    /**
     * Returns the configured difficulty.
     *
     * @return difficulty in leading zero bits
     */
    public int getDifficulty() {
        return difficulty;
    }

    /**
     * Validates whether the candidate block satisfies the proof-of-work rules.
     *
     * @param block the candidate block to validate
     * @param chain the current blockchain state
     * @return {@code true} if the block is valid for this engine
     * @throws NullPointerException if {@code block} or {@code chain} is null
     */
    @Override
    public boolean validateBlock(Block block, Blockchain chain) {
        Objects.requireNonNull(block, "block must not be null");
        Objects.requireNonNull(chain, "chain must not be null");

        if (ConsensusSupport.isGenesis(block)) {
            return true;
        }

        return ConsensusSupport.hasConsistentIntegrity(block)
            && block.getHeader().bits() == difficulty
            && ConsensusSupport.hasLeadingZeroBits(block.getHash(), difficulty);
    }

    /**
     * Mines a new block by searching for a nonce that satisfies the configured
     * leading-zero-bit target.
     *
     * @param transactions  ordered transactions to include in the block
     * @param previousBlock the current chain tip
     * @return a newly mined block
     * @throws NullPointerException if {@code transactions} or {@code previousBlock} is null
     * @throws ConsensusException   if mining is interrupted or the nonce search is exhausted
     */
    @Override
    public Block mineBlock(List<Transaction> transactions, Block previousBlock) {
        Objects.requireNonNull(transactions, "transactions must not be null");
        Objects.requireNonNull(previousBlock, "previousBlock must not be null");

        Instant timestamp = Instant.now();
        for (long nonce = 0L; nonce < Long.MAX_VALUE; nonce++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new ConsensusException("PoW mining interrupted before a valid nonce was found");
            }

            Block candidate = ConsensusSupport.buildBlock(
                previousBlock,
                transactions,
                difficulty,
                nonce,
                null,
                timestamp);

            if (ConsensusSupport.hasLeadingZeroBits(candidate.getHash(), difficulty)) {
                return candidate;
            }
        }

        throw new ConsensusException("PoW mining failed to find a valid nonce within the supported range");
    }

    /**
     * Returns the stable name used to identify this engine in logs and status output.
     *
     * @return the engine name
     */
    @Override
    public String engineName() {
        return "ProofOfWork";
    }
}

