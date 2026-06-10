/**
 * Lifecycle interfaces for network subsystems, injected by {@code blockchain-network}.
 *
 * <p>These interfaces decouple {@code blockchain-core} from the Netty implementation
 * in {@code blockchain-network}, preserving the zero-mandatory-dependency contract
 * of the core module (design.md §7.1, ADR-001).</p>
 *
 * <p>Interfaces:</p>
 * <ul>
 *   <li>{@link com.privatechain.core.network.NodeServerLifecycle} — start/stop contract
 *       for the TCP server that accepts inbound peer connections.</li>
 *   <li>{@link com.privatechain.core.network.PeerManagerLifecycle} — start/stop contract
 *       for the component that manages peer connections and heartbeats.</li>
 *   <li>{@link com.privatechain.core.network.ChainSyncer} — contract for the component
 *       that synchronizes the local chain with the network on startup.</li>
 * </ul>
 *
 * @since 1.0.0
 */
package com.privatechain.core.network;
