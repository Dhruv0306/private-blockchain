/**
 * Peer-to-peer networking layer for the private-blockchain library — built on
 * Netty 4.2 TCP with NDJSON message framing (ADR-001).
 *
 * <p>Four sub-packages form the networking stack:</p>
 * <ul>
 *   <li>{@link com.privatechain.network.peer} — peer lifecycle management:
 *       connection, heartbeat, and pruning.</li>
 *   <li>{@link com.privatechain.network.gossip} — transaction gossip and block
 *       broadcast to connected peers.</li>
 *   <li>{@link com.privatechain.network.sync} — chain synchronization on startup
 *       and fork resolution.</li>
 *   <li>{@link com.privatechain.network.rpc} — Netty server/client bootstrap and
 *       the NDJSON message codec.</li>
 * </ul>
 *
 * <p>This module depends on {@code blockchain-access} so that
 * {@link com.privatechain.access.allowlist.AllowlistManager} can gate every inbound
 * message before any application-layer processing occurs (NFR-SEC-04).</p>
 *
 * <p><strong>Transport note:</strong> The current implementation uses plain TCP with
 * NDJSON framing. A migration to gRPC/TLS is tracked in the post-1.0 backlog as
 * T-B02; see {@code docs/decisions/ADR-001-transport.md} for the rationale.</p>
 *
 * @since 1.0.0
 */
package com.privatechain.network;
