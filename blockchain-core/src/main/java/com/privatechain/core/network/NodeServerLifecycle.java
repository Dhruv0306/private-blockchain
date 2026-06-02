package com.privatechain.core.network;

/**
 * Lifecycle interface for the P2P TCP node server.
 *
 * <p>Defined in {@code blockchain-core} so that {@link com.privatechain.core.builder.BlockchainNode}
 * can control the server lifecycle without a hard dependency on the {@code blockchain-network}
 * module or Netty (design.md §7.1 — dependency inversion principle).</p>
 *
 * <p>The concrete {@code NodeServer} implementation lives in {@code blockchain-network}
 * and is injected via
 * {@link com.privatechain.core.builder.BlockchainNode#setNodeServer(NodeServerLifecycle)}
 * during node assembly.</p>
 *
 * @see com.privatechain.core.builder.BlockchainNode
 * @since 1.0.0
 */
public interface NodeServerLifecycle {

    /**
     * Binds the server to its configured TCP port and starts accepting inbound
     * peer connections.
     *
     * @throws IllegalStateException if the server is already running
     */
    void start();

    /**
     * Stops the server and closes the listening socket.
     *
     * <p>In-flight connections are closed gracefully where possible. After this
     * call, {@link #isRunning()} returns {@code false}.</p>
     */
    void stop();

    /**
     * Returns {@code true} if the server is currently accepting inbound connections.
     *
     * @return {@code true} if running
     */
    boolean isRunning();
}
