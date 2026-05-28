package com.privatechain.core.network;

/**
 * Lifecycle and peer-count contract for the optional peer manager.
 *
 * <p>Defined in {@code blockchain-core} so that {@link com.privatechain.core.builder.BlockchainNode}
 * can reference the peer manager without depending on {@code blockchain-network}.
 * The concrete implementation {@code com.privatechain.network.peer.PeerManager}
 * implements this interface.</p>
 *
 * @since 1.0.0
 */
public interface PeerManagerLifecycle {

    /**
     * Starts the peer manager and schedules the periodic heartbeat task.
     */
    void start();

    /**
     * Stops the peer manager, disconnects all peers, and shuts down the scheduler.
     */
    void stop();

    /**
     * Returns the number of currently connected peers.
     *
     * @return connected peer count (&ge; 0)
     */
    int getConnectedPeerCount();
}
