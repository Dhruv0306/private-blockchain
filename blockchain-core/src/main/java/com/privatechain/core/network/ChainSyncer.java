package com.privatechain.core.network;

/**
 * Interface for the initial chain synchronization component.
 *
 * <p>Defined in {@code blockchain-core} so that
 * {@link com.privatechain.core.builder.BlockchainNode} can trigger chain sync on
 * startup without a hard dependency on the {@code blockchain-network} module
 * (design.md §7.1 — dependency inversion principle).</p>
 *
 * <p>The concrete {@code SyncManager} implementation in {@code blockchain-network}
 * broadcasts a {@code GET_STATUS} message to all connected peers, identifies the
 * peer with the highest chain, fetches missing blocks, validates each one via the
 * configured {@link com.privatechain.core.spi.ConsensusEngine}, and appends them
 * via {@link com.privatechain.core.builder.Blockchain#addBlock(com.privatechain.core.model.Block)}
 * (design.md §4.3 — peer sync flow).</p>
 *
 * @see com.privatechain.core.builder.BlockchainNode
 * @since 1.0.0
 */
public interface ChainSyncer {

    /**
     * Synchronizes the local chain with the network.
     *
     * <p>Fetches and applies all blocks that the local node is missing, starting from
     * {@code localChainHeight + 1} up to the canonical chain height reported by the
     * best peer. Returns {@code 0} if the local chain is already up-to-date.</p>
     *
     * @return the number of blocks appended during this sync operation (&ge; 0)
     */
    int syncChain();
}
