package com.privatechain.examples;

import com.privatechain.consensus.ConsensusSupport;
import com.privatechain.core.builder.Blockchain;
import com.privatechain.core.model.Block;
import com.privatechain.core.model.Transaction;
import com.privatechain.core.spi.ConsensusEngine;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A majority-vote consensus engine where block acceptance requires explicit
 * approval from a strict majority of registered validators.
 *
 * <p>This class is the canonical example of a user-defined {@link ConsensusEngine}
 * implementation, demonstrating FR-CONS-06 (custom engine injectable via
 * {@link com.privatechain.core.builder.BlockchainConfig#builder()}) and AC-08
 * (custom engine is called for every {@code addBlock()} invocation).</p>
 *
 * <h2>Protocol</h2>
 * <ol>
 *   <li>A proposer calls {@link #mineBlock} to assemble a candidate block.
 *       {@code mineBlock} does NOT require any hash search — block production
 *       is instant.</li>
 *   <li>Each registered validator calls {@link #castVote(String, String)} with the
 *       candidate block's hash and their own validator address.</li>
 *   <li>When {@link #validateBlock} is called (by {@code Blockchain.addBlock}), it
 *       counts the votes recorded for that hash and requires a strict majority:
 *       {@code votes > floor(validatorCount / 2)}.</li>
 * </ol>
 *
 * <h2>Demo simplification</h2>
 * <p>In this demo, {@link #mineBlock} automatically casts votes from all registered
 * validators. In a real distributed system, votes would arrive asynchronously from
 * independent validator nodes over the network before {@code addBlock} is called.</p>
 *
 * <h2>Thread safety</h2>
 * <p>Vote casting and vote counting are thread-safe via {@link ConcurrentHashMap}.
 * Validator registration is immutable after construction.</p>
 *
 * @see ConsensusEngine
 * @since 1.0.0
 */
public final class VotingConsensusEngine implements ConsensusEngine {

    // ─── Immutable validator registry ─────────────────────────────────────────

    /**
     * Ordered, immutable list of registered validator addresses.
     * Sorted lexicographically at construction time for deterministic quorum calculation.
     */
    private final List<String> validators;

    // ─── Vote state (concurrent) ──────────────────────────────────────────────

    /**
     * Maps block hash → set of validator addresses that have voted FOR that block.
     * Uses a {@link ConcurrentHashMap} of {@link ConcurrentHashMap#newKeySet()} sets
     * so concurrent vote casting is safe without external synchronization.
     */
    private final Map<String, Set<String>> votes = new ConcurrentHashMap<>();

    // ─── Constructor ──────────────────────────────────────────────────────────

    /**
     * Constructs a {@code VotingConsensusEngine} with the given validator set.
     *
     * <p>At least one validator is required. The list is defensively copied and
     * sorted for deterministic behavior across environments.</p>
     *
     * @param validators ordered collection of unique validator address strings (non-null, non-empty)
     * @throws NullPointerException     if {@code validators} is null or any element is null
     * @throws IllegalArgumentException if the collection is empty or any element is blank
     */
    public VotingConsensusEngine(Collection<String> validators) {
        Objects.requireNonNull(validators, "validators must not be null");
        if (validators.isEmpty()) {
            throw new IllegalArgumentException(
                "VotingConsensusEngine requires at least one registered validator");
        }
        for (String v : validators) {
            Objects.requireNonNull(v, "validator address must not be null");
            if (v.isBlank()) {
                throw new IllegalArgumentException(
                    "validator address must not be blank; found: '" + v + "'");
            }
        }
        // Defensive copy + sort for deterministic quorum results
        this.validators = validators.stream().sorted().toList();
    }

    // ─── Vote casting ─────────────────────────────────────────────────────────

    /**
     * Records a validator's approval vote for the block identified by {@code blockHash}.
     *
     * <p>Calling this method multiple times for the same {@code (blockHash, validatorAddress)}
     * pair is idempotent — only one vote per validator per block is counted.</p>
     *
     * @param blockHash        hex-encoded hash of the block to vote for (non-null, non-blank)
     * @param validatorAddress address of the voting validator; must be in the registered set
     * @throws NullPointerException     if either argument is null
     * @throws IllegalArgumentException if {@code blockHash} is blank or
     *                                  {@code validatorAddress} is not registered
     */
    public void castVote(String blockHash, String validatorAddress) {
        Objects.requireNonNull(blockHash, "blockHash must not be null");
        Objects.requireNonNull(validatorAddress, "validatorAddress must not be null");
        if (blockHash.isBlank()) {
            throw new IllegalArgumentException("blockHash must not be blank");
        }
        if (!validators.contains(validatorAddress)) {
            throw new IllegalArgumentException(
                "Unknown validator '" + validatorAddress
                    + "'. Registered validators: " + validators);
        }
        // computeIfAbsent guarantees a thread-safe, non-null set; add() is idempotent
        votes.computeIfAbsent(blockHash, k -> ConcurrentHashMap.newKeySet())
            .add(validatorAddress);
    }

    // ─── ConsensusEngine SPI ──────────────────────────────────────────────────

    /**
     * Validates a candidate block by checking that a strict majority of registered
     * validators have cast a vote for its hash.
     *
     * <p>The genesis block (index 0) is always accepted unconditionally — it is
     * created by {@link com.privatechain.core.builder.GenesisBlockFactory} before
     * any voting infrastructure is available.</p>
     *
     * <p>Quorum formula: {@code voteCount &gt; floor(validatorCount / 2)}</p>
     *
     * <ul>
     *   <li>1 validator: quorum = 1 (unanimity)</li>
     *   <li>2 validators: quorum = 2 (unanimity)</li>
     *   <li>3 validators: quorum = 2 (simple majority)</li>
     *   <li>5 validators: quorum = 3 (simple majority)</li>
     * </ul>
     *
     * @param block the candidate block to validate (non-null)
     * @param chain the current blockchain state (non-null)
     * @return {@code true} if the block has sufficient votes or is the genesis block;
     * {@code false} otherwise
     * @throws NullPointerException if either argument is null
     */
    @Override
    public boolean validateBlock(Block block, Blockchain chain) {
        Objects.requireNonNull(block, "block must not be null");
        Objects.requireNonNull(chain, "chain must not be null");

        // Genesis is always valid — no voting needed
        if (ConsensusSupport.isGenesis(block)) {
            return true;
        }

        // Count distinct validator votes for this block's hash
        Set<String> blockVotes = votes.getOrDefault(block.getHash(), Set.of());
        int quorum = validators.size() / 2 + 1;  // strict majority

        return blockVotes.size() >= quorum;
    }

    /**
     * Assembles a new candidate block and automatically casts votes from all
     * registered validators (demo simplification).
     *
     * <p>Block production is instant — this engine has no proof-of-work mining loop.
     * The produced block uses {@code bits=0} and {@code nonce=0} because the engine's
     * security model relies on validator signatures, not hash difficulty.</p>
     *
     * <p><strong>Demo note:</strong> In a real distributed system, votes would be
     * collected asynchronously from independent validator nodes over the P2P network
     * before {@code Blockchain.addBlock()} is called. Auto-voting here makes the
     * demo self-contained and runnable without a live network.</p>
     *
     * @param transactions  the ordered transactions to include in the block (non-null)
     * @param previousBlock the current chain tip (non-null)
     * @return a new candidate block with all validator votes pre-cast
     * @throws NullPointerException if either argument is null
     */
    @Override
    public Block mineBlock(List<Transaction> transactions, Block previousBlock) {
        Objects.requireNonNull(transactions, "transactions must not be null");
        Objects.requireNonNull(previousBlock, "previousBlock must not be null");

        // Build the candidate block — no PoW: bits=0, nonce=0, minerAddress=null
        Block candidate = ConsensusSupport.buildBlock(
            previousBlock,
            transactions,
            0,          // bits: no difficulty target
            0L,         // nonce: not used by this engine
            null,       // minerAddress: no single miner in a voting scheme
            Instant.now());

        // Auto-cast votes from every registered validator (demo simplification)
        for (String validator : validators) {
            castVote(candidate.getHash(), validator);
        }

        return candidate;
    }

    /**
     * Returns the human-readable name used in logs and {@link com.privatechain.core.builder.BlockchainNode#status()}.
     *
     * @return {@code "VotingConsensus"}
     */
    @Override
    public String engineName() {
        return "VotingConsensus";
    }

    // ─── Inspection helpers (for tests and demos) ─────────────────────────────

    /**
     * Returns an immutable copy of the registered validator list.
     *
     * @return non-null, non-empty, sorted, immutable list of validator addresses
     */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "already immutable from List.copyOf in constructor")
    public List<String> getValidators() {
        return validators; // already immutable from List.copyOf in constructor
    }

    /**
     * Returns the number of distinct validator votes recorded for a given block hash.
     *
     * @param blockHash hex-encoded block hash (non-null)
     * @return vote count (&ge; 0); returns 0 if no votes have been cast for this hash
     * @throws NullPointerException if {@code blockHash} is null
     */
    public int getVoteCount(String blockHash) {
        Objects.requireNonNull(blockHash, "blockHash must not be null");
        return votes.getOrDefault(blockHash, Set.of()).size();
    }

    /**
     * Returns the strict-majority quorum threshold for the registered validator set.
     *
     * <p>A block requires at least this many votes to pass {@link #validateBlock}.</p>
     *
     * @return minimum votes required for block acceptance
     */
    public int getQuorum() {
        return validators.size() / 2 + 1;
    }

    /**
     * Removes all recorded votes. Intended for testing only.
     *
     * <p>Calling this method on a live node will cause all pending blocks to fail
     * {@link #validateBlock} until they receive fresh votes.</p>
     */
    public void clearVotes() {
        votes.clear();
    }
}
