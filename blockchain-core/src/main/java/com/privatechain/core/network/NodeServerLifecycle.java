package com.privatechain.core.network;

/**
 * Lifecycle contract for the optional TCP node server.
 *
 * <p>Defined in {@code blockchain-core} so that {@link com.privatechain.core.builder.BlockchainNode}
 * can reference the server without depending on {@code blockchain-network}.
 * The concrete implementation {@code com.privatechain.network.rpc.NodeServer}
 * implements this interface.</p>
 *
 * @since 1.0.0
 */
public interface NodeServerLifecycle {

    /**
     * Binds the server socket and starts accepting inbound connections.
     *
     * @throws IllegalStateException if the server is already running
     */
    void start();

    /**
     * Closes the server socket and shuts down the connection handler pool.
     */
    void stop();

    /**
     * Returns {@code true} if the server accept loop is currently running.
     *
     * @return {@code true} if running
     */
    boolean isRunning();
}
