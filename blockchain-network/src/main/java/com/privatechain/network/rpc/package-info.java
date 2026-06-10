/**
 * Netty TCP server, client, and NDJSON message codec — the transport layer of the
 * P2P networking stack (FR-NET-01, ADR-001).
 *
 * <p>Classes in this package:</p>
 * <ul>
 *   <li>{@link com.privatechain.network.rpc.NodeServer} — Netty {@code ServerBootstrap}
 *       that accepts inbound peer connections on the configured port. Each accepted
 *       connection is checked against
 *       {@link com.privatechain.access.allowlist.AllowlistManager} before any
 *       message is dispatched (NFR-SEC-04).</li>
 *   <li>{@link com.privatechain.network.rpc.NodeClient} — manages outbound connections
 *       to remote peers; used by {@link com.privatechain.network.peer.PeerManager}
 *       during initial connection and reconnection.</li>
 *   <li>{@link com.privatechain.network.rpc.MessageCodec} — encodes and decodes
 *       wire-protocol messages using NDJSON framing (one JSON object per line,
 *       terminated by {@code \n}). Each message carries a {@code type} discriminator
 *       field for routing.</li>
 * </ul>
 *
 * <p>The {@code proto/} subdirectory contains the Protobuf IDL
 * ({@code blockchain.proto}) reserved for a future gRPC migration (T-B02).</p>
 *
 * @since 1.0.0
 */
package com.privatechain.network.rpc;
