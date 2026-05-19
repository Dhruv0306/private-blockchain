package com.privatechain.consensus.poa;

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
 * Proof-of-authority consensus engine.
 *
 * <p>This engine models a private-chain authority scheme where block production is
 * restricted to a fixed set of pre-approved miner addresses. The implementation keeps
 * block production deterministic by selecting the lexicographically first configured
 * address when mining a block.</p>
 *
 * <p>Validation checks that the block hash is internally consistent and that the
 * claimed miner belongs to the configured authorization set.</p>
 *
 * @since 1.0.0
 */
public final class ProofOfAuthorityEngine implements ConsensusEngine {

    private final List<String> authorizedAddresses;

    /**
     * Creates a proof-of-authority engine with the supplied authorized addresses.
     *
     * @param authorizedAddresses collection of node addresses permitted to produce blocks
     * @throws NullPointerException     if {@code authorizedAddresses} is null
     * @throws IllegalArgumentException if the collection contains blank entries
     */
    public ProofOfAuthorityEngine(Collection<String> authorizedAddresses) {
        this.authorizedAddresses = ConsensusSupport.copyAndValidate(authorizedAddresses,
            "authorizedAddresses", true);
    }

    /**
     * Returns the configured authorized addresses in deterministic order.
     *
     * @return immutable list of authorized miner addresses
     */
    public List<String> getAuthorizedAddresses() {
        return List.copyOf(authorizedAddresses);
    }

    /**
     * Validates whether the candidate block was produced by an authorized miner.
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
            && block.getHeader().bits() == 0
            && block.getHeader().nonce() == 0L
            && block.getMinerAddress() != null
            && authorizedAddresses.contains(block.getMinerAddress());
    }

    /**
     * Builds a new authority-signed block using the first authorized miner address.
     *
     * @param transactions  ordered transactions to include in the block
     * @param previousBlock the current chain tip
     * @return a newly produced block
     * @throws NullPointerException if {@code transactions} or {@code previousBlock} is null
     * @throws ConsensusException   if no authorized addresses are configured
     */
    @Override
    public Block mineBlock(List<Transaction> transactions, Block previousBlock) {
        Objects.requireNonNull(transactions, "transactions must not be null");
        Objects.requireNonNull(previousBlock, "previousBlock must not be null");

        if (authorizedAddresses.isEmpty()) {
            throw new ConsensusException("PoA requires at least one authorized miner address");
        }

        String minerAddress = authorizedAddresses.get(0);
        return ConsensusSupport.buildBlock(previousBlock, transactions, 0, 0L, minerAddress, Instant.now());
    }

    /**
     * Returns the stable name used to identify this engine in logs and status output.
     *
     * @return the engine name
     */
    @Override
    public String engineName() {
        return "ProofOfAuthority";
    }
}

