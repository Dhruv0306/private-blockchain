/**
 * Peer lifecycle management — maintains the list of connected peers, runs heartbeat
 * checks, and prunes unresponsive connections (FR-NET-01, FR-NET-07).
 *
 * <p>Classes in this package:</p>
 * <ul>
 *   <li>{@link com.privatechain.network.peer.Peer} — immutable value object
 *       representing a remote peer: {@code nodeId}, {@code host}, {@code port},
 *       {@code publicKey}, and {@code lastSeen} timestamp.</li>
 *   <li>{@link com.privatechain.network.peer.PeerManager} — connects to seed peers,
 *       runs a ping/pong heartbeat every 30 seconds, and removes peers that have
 *       not responded within the configured timeout. Publishes
 *       {@link com.privatechain.core.event.BlockchainEvent.PeerConnectedEvent} and
 *       {@link com.privatechain.core.event.BlockchainEvent.PeerDisconnectedEvent}.</li>
 *   <li>{@link com.privatechain.network.peer.PeerStore} — persists known peer
 *       addresses to {@link com.privatechain.core.spi.BlockchainStorage} so the
 *       peer set is available after a node restart.</li>
 * </ul>
 *
 * <p>Maximum simultaneous peer connections is configurable; the default is 25
 * (FR-NET-07).</p>
 *
 * @since 1.0.0
 */
package com.privatechain.network.peer;
