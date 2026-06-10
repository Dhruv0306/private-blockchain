/**
 * Chain synchronization and fork resolution — brings a node's local chain up to date
 * with the network on startup and after a network partition (FR-NET-04, FR-NET-05).
 *
 * <p>Classes in this package:</p>
 * <ul>
 *   <li>{@link com.privatechain.network.sync.SyncManager} — on node start, broadcasts
 *       a {@code GET_STATUS} request to all peers, collects their chain heights, and
 *       requests missing blocks from the peer reporting the highest height.
 *       Also triggered after a partition is detected (NFR-REL-02).</li>
 *   <li>{@link com.privatechain.network.sync.BlockFetcher} — requests a range of
 *       blocks from a specific remote peer and validates each block via
 *       {@link com.privatechain.core.spi.ConsensusEngine} before appending it to
 *       the local chain.</li>
 *   <li>{@link com.privatechain.network.sync.ForkResolver} — when two competing chain
 *       tips are detected, selects the chain with the greater cumulative difficulty
 *       as the canonical chain and publishes
 *       {@link com.privatechain.core.event.BlockchainEvent.ForkDetectedEvent}
 *       (FR-NET-05).</li>
 * </ul>
 *
 * @since 1.0.0
 */
package com.privatechain.network.sync;
