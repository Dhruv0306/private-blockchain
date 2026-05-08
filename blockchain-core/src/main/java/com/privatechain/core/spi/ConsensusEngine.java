package com.privatechain.core.spi;

import com.privatechain.core.model.Block;
import com.privatechain.core.model.Transaction;

import java.util.List;

/**
 * Service Provider Interface (SPI) for pluggable consensus algorithms.
 *
 * <p>Implement this interface to define how new blocks are produced and how
 * existing blocks are validated. The library ships four built-in implementations
 * in the {@code blockchain-consensus} module:</p>
 * <ul>
 *   <li>{@code ProofOfWorkEngine} — SHA-256 mining with configurable difficulty</li>
 *   <li>{@code ProofOfAuthorityEngine} — authorized-signer allowlist</li>
 *   <li>{@code PBFTEngine} — practical Byzantine fault-tolerance (3-phase commit)</li>
 *   <li>{@code RoundRobinEngine} — deterministic slot-based rotation (dev/test)</li>
 * </ul>
 *
 * <p>Inject your implementation via {@link com.privatechain.core.builder.BlockchainConfig}:</p>
 * <pre>{@code
 * BlockchainNode node = BlockchainConfig.builder()
 *     .consensusEngine(new MyCustomEngine())
 *     .build();
 * }</pre>
 *
 * <p><strong>Thread safety:</strong> Implementations must be safe for concurrent
 * calls to {@link #validateBlock} (e.g., during sync) but {@link #mineBlock} is
 * called from a single producer thread per node.</p>
 *
 * @see com.privatechain.core.builder.BlockchainConfig
 * @see ValidationResult
 * @since 1.0.0
 */
public interface ConsensusEngine {

    /**
     * Validates whether the given block satisfies the consensus rules of this engine.
     *
     * <p>Implementations should verify engine-specific invariants such as:</p>
     * <ul>
     *   <li>PoW: {@code block.getHash()} meets the required difficulty target</li>
     *   <li>PoA: {@code block.getHeader()} is signed by an authorized node</li>
     *   <li>PBFT: block carries the required quorum of pre-commit signatures</li>
     * </ul>
     *
     * <p>This method is called by {@link com.privatechain.core.builder.Blockchain#addBlock}
     * before the block is appended to the chain. Returning {@code false} causes
     * {@code addBlock} to throw a
     * {@link com.privatechain.core.exception.BlockValidationException}.</p>
     *
     * @param block the candidate block to validate (non-null)
     * @param chain the current blockchain state at the time of validation (non-null)
     * @return {@code true} if the block is valid according to this engine's rules
     * @throws com.privatechain.core.exception.ConsensusException if an unrecoverable
     *                                                            error occurs during validation (e.g., network quorum unreachable in PBFT)
     */
    boolean validateBlock(Block block, com.privatechain.core.builder.Blockchain chain);

    /**
     * Produces a new candidate block from the given transactions and the current
     * chain tip.
     *
     * <p>For mining-based engines (PoW) this method blocks until a valid nonce is
     * found. For authority-based engines (PoA, Round-Robin) it returns almost
     * immediately after signing. For PBFT, it initiates the multiphase protocol
     * and returns once a quorum has been reached.</p>
     *
     * <p>The returned block must pass {@link #validateBlock} when called on the
     * same engine instance.</p>
     *
     * @param transactions  ordered list of transactions to include (non-null; may be empty)
     * @param previousBlock the current chain tip that the new block will extend (non-null)
     * @return a fully formed, consensus-ready block ready to be passed to
     * {@link com.privatechain.core.builder.Blockchain#addBlock}
     * @throws com.privatechain.core.exception.ConsensusException if block production fails
     *                                                            (e.g., not enough peers for PBFT quorum)
     */
    Block mineBlock(List<Transaction> transactions, Block previousBlock);

    /**
     * Returns a human-readable name identifying this consensus engine.
     *
     * <p>Used in log messages, metrics labels, and the node status response.
     * Must be stable across restarts (i.e., don't include a random UUID).</p>
     *
     * @return non-null, non-blank engine name (e.g., {@code "ProofOfWork"}, {@code "PBFT"})
     */
    String engineName();
}
