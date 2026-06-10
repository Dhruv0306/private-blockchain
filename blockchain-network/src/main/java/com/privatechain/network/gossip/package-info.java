/**
 * Transaction gossip and block broadcast — propagates newly submitted transactions
 * and mined blocks to connected peers (FR-NET-02, FR-NET-03).
 *
 * <p>Classes in this package:</p>
 * <ul>
 *   <li>{@link com.privatechain.network.gossip.GossipProtocol} — on
 *       {@link com.privatechain.core.event.BlockchainEvent.TransactionSubmittedEvent},
 *       forwards the transaction to {@code ceil(log2(n))} randomly selected peers
 *       (configurable fan-out {@code k}). This logarithmic fan-out achieves full
 *       network coverage in O(log n) hops.</li>
 *   <li>{@link com.privatechain.network.gossip.BlockBroadcaster} — on
 *       {@link com.privatechain.core.event.BlockchainEvent.BlockAddedEvent},
 *       pushes the serialized block to all connected peers simultaneously.</li>
 * </ul>
 *
 * @since 1.0.0
 */
package com.privatechain.network.gossip;
