package com.privatechain.core.network;

/**
 * Lifecycle interface for the P2P peer manager.
 *
 * <p>Defined in {@code blockchain-core} so that {@link com.privatechain.core.builder.BlockchainNode}
 * can reference the peer manager without introducing a hard dependency on the
 * {@code blockchain-network} module (design.md §7.1 — zero mandatory transitive deps
 * on {@code blockchain-core}).</p>
 *
 * <p>The concrete implementation {@code PeerManager} lives in {@code blockchain-network}
 * and is injected via
 * {@link com.privatechain.core.builder.BlockchainNode#setPeerManager(PeerManagerLifecycle)}
 * during node assembly.</p>
 *
 * @see com.privatechain.core.builder.BlockchainNode
 * @since 1.0.0
 */
public interface PeerManagerLifecycle {

    /**
     * Starts the peer manager (heartbeat scheduler, seed peer connections, etc.).
     *
     * @throws IllegalStateException if already started
     */
    void start();

    /**
     * Stops the peer manager and disconnects all active peers cleanly.
     */
    void stop();

    /**
     * Returns the number of currently connected peers.
     *
     * @return connected peer count (&ge; 0)
     */
    int getConnectedPeerCount();
}
