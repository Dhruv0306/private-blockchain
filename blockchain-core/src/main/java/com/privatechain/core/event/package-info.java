/**
 * Sealed event hierarchy and the asynchronous publish-subscribe event bus.
 *
 * <p>All blockchain state changes are communicated through this package:</p>
 * <ul>
 *   <li>{@link com.privatechain.core.event.BlockchainEvent} — sealed base type for all
 *       events; five permitted subtypes: {@code BlockAddedEvent}, {@code TransactionSubmittedEvent},
 *       {@code PeerConnectedEvent}, {@code PeerDisconnectedEvent}, {@code ForkDetectedEvent}
 *       (FR-EVENT-01).</li>
 *   <li>{@link com.privatechain.core.event.BlockchainEventBus} — thread-safe event bus
 *       that dispatches events asynchronously to registered listeners via a daemon
 *       executor (FR-EVENT-03).</li>
 *   <li>{@link com.privatechain.core.event.BlockchainEventListener} — functional interface
 *       for receiving events; multiple implementations may be registered (FR-EVENT-02).</li>
 * </ul>
 *
 * @since 1.0.0
 */
package com.privatechain.core.event;
