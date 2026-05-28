package com.privatechain.network.peer;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable value object representing a remote peer node in the private blockchain network.
 *
 * <p>A {@code Peer} encapsulates all the addressing and identity information needed to
 * establish and maintain a TCP connection to another network participant. It is used by
 * {@link PeerManager} to track the set of known nodes and by {@link com.privatechain.network.rpc.NodeClient}
 * to open outbound connections (design.md §3, FR-NET-01).</p>
 *
 * <h2>Identity vs address</h2>
 * <p>The {@code nodeId} is a stable logical identifier (e.g., a hex-encoded public key hash)
 * that persists across reconnections. The {@code host}/{@code port} pair is the current
 * physical location of the peer, which may change if the peer restarts on a different address.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * Peer peer = Peer.builder()
 *     .nodeId("ab12cd34ef56...")
 *     .host("192.168.1.10")
 *     .port(8545)
 *     .publicKeyHex("04aabb...")
 *     .build();
 *
 * // Record a heartbeat
 * Peer refreshed = peer.withLastSeen(Instant.now());
 * }</pre>
 *
 * @see PeerManager
 * @since 1.0.0
 */
public final class Peer {

    // ─── Fields ───────────────────────────────────────────────────────────────

    /**
     * Stable logical identifier for this peer (e.g., hex-encoded public key hash).
     * Must be unique across the network; used as the allowlist key (FR-AC-01).
     */
    private final String nodeId;

    /**
     * Hostname or IPv4/IPv6 address of the remote node.
     */
    private final String host;

    /**
     * TCP port on which the remote node's {@link com.privatechain.network.rpc.NodeServer} listens.
     */
    private final int port;

    /**
     * Hex-encoded secp256k1 public key of the remote node.
     * Used to verify block signatures and invitation tokens.
     * May be {@code null} if the handshake has not yet completed.
     */
    private final String publicKeyHex;

    /**
     * UTC instant of the most recent successful heartbeat or message exchange.
     * Used by {@link PeerManager} to detect and prune unresponsive peers.
     * {@code null} for newly discovered peers that have never been contacted.
     */
    private final Instant lastSeen;

    // ─── Constructor ──────────────────────────────────────────────────────────

    /**
     * Constructs a {@code Peer} with all fields.
     *
     * <p>Prefer {@link #builder()} for fluent construction; this constructor is
     * package-private for use by the builder and test fixtures.</p>
     *
     * @param nodeId       stable logical identifier (non-null, non-blank)
     * @param host         hostname or IP address (non-null, non-blank)
     * @param port         TCP port (1024–65535)
     * @param publicKeyHex hex-encoded public key; may be null before handshake
     * @param lastSeen     last contact timestamp; may be null for new peers
     * @throws NullPointerException     if nodeId or host is null
     * @throws IllegalArgumentException if nodeId or host is blank, or if port is out of range
     */
    Peer(String nodeId, String host, int port, String publicKeyHex, Instant lastSeen) {
        this.nodeId = requireNonBlank(nodeId, "nodeId");
        this.host = requireNonBlank(host, "host");
        if (port < 1024 || port > 65535) {
            throw new IllegalArgumentException("Port must be in [1024, 65535], got: " + port);
        }
        this.port = port;
        this.publicKeyHex = publicKeyHex; // nullable — not yet authenticated
        this.lastSeen = lastSeen;         // nullable — never contacted yet
    }

    // ─── Factory ──────────────────────────────────────────────────────────────

    /**
     * Returns a new {@link Builder} for constructing a {@code Peer}.
     *
     * @return a fresh, empty builder
     */
    public static Builder builder() {
        return new Builder();
    }

    // ─── Accessors ────────────────────────────────────────────────────────────

    /**
     * Validates that a string field is neither null nor blank.
     *
     * @param value     the value to check
     * @param fieldName used in exception messages
     * @return the validated value
     * @throws NullPointerException     if value is null
     * @throws IllegalArgumentException if value is blank
     */
    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    /**
     * Returns the stable logical identifier of this peer.
     *
     * @return non-null, non-blank node ID
     */
    public String getNodeId() {
        return nodeId;
    }

    /**
     * Returns the hostname or IP address of this peer.
     *
     * @return non-null, non-blank host string
     */
    public String getHost() {
        return host;
    }

    /**
     * Returns the TCP port on which this peer's node server listens.
     *
     * @return port in range [1024, 65535]
     */
    public int getPort() {
        return port;
    }

    /**
     * Returns the hex-encoded secp256k1 public key of this peer.
     *
     * <p>May return {@code null} if the key exchange handshake has not yet
     * completed (e.g., for a freshly discovered peer).</p>
     *
     * @return hex-encoded public key, or {@code null} before handshake
     */
    public String getPublicKeyHex() {
        return publicKeyHex;
    }

    /**
     * Returns the UTC instant of the most recent heartbeat or message exchange.
     *
     * <p>Returns {@code null} for peers that have been added to the peer list
     * but have not yet been contacted.</p>
     *
     * @return last-seen timestamp, or {@code null}
     */
    public Instant getLastSeen() {
        return lastSeen;
    }

    // ─── Derived copy helpers ─────────────────────────────────────────────────

    /**
     * Returns the network address in {@code host:port} notation.
     *
     * @return formatted address string (e.g. {@code "192.168.1.10:8545"})
     */
    public String getAddress() {
        return host + ":" + port;
    }

    /**
     * Returns a new {@code Peer} with the {@code lastSeen} field updated to the given instant.
     *
     * <p>All other fields are carried over unchanged. This method is used by
     * {@link PeerManager} on every successful heartbeat or message exchange.</p>
     *
     * @param lastSeen updated last-seen timestamp (non-null)
     * @return new {@code Peer} instance with updated timestamp
     * @throws NullPointerException if lastSeen is null
     */
    public Peer withLastSeen(Instant lastSeen) {
        Objects.requireNonNull(lastSeen, "lastSeen must not be null");
        return new Peer(nodeId, host, port, publicKeyHex, lastSeen);
    }

    // ─── Object overrides ─────────────────────────────────────────────────────

    /**
     * Returns a new {@code Peer} with the public key filled in after handshake.
     *
     * @param publicKeyHex hex-encoded secp256k1 public key (non-null, non-blank)
     * @return new {@code Peer} instance with populated public key
     * @throws NullPointerException     if publicKeyHex is null
     * @throws IllegalArgumentException if publicKeyHex is blank
     */
    public Peer withPublicKey(String publicKeyHex) {
        return new Peer(nodeId, host, port, requireNonBlank(publicKeyHex, "publicKeyHex"), lastSeen);
    }

    /**
     * Two peers are equal if and only if their {@code nodeId}s are equal.
     *
     * @param obj the object to compare
     * @return {@code true} if both peers have the same node ID
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Peer other)) return false;
        return Objects.equals(nodeId, other.nodeId);
    }

    /**
     * Hash code based solely on {@code nodeId} for consistency with {@link #equals}.
     *
     * @return hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(nodeId);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Returns a human-readable summary of this peer (safe for logging).
     *
     * @return string representation
     */
    @Override
    public String toString() {
        return "Peer{"
            + "nodeId=" + nodeId
            + ", address=" + getAddress()
            + ", lastSeen=" + lastSeen
            + '}';
    }

    // ─── Builder ──────────────────────────────────────────────────────────────

    /**
     * Fluent builder for {@link Peer}.
     *
     * <pre>{@code
     * Peer peer = Peer.builder()
     *     .nodeId("ab12cd...")
     *     .host("10.0.0.5")
     *     .port(8545)
     *     .build();
     * }</pre>
     */
    public static final class Builder {

        private String nodeId;
        private String host;
        private int port = 8545; // default P2P port
        private String publicKeyHex;
        private Instant lastSeen;

        /**
         * Package-private — use {@link Peer#builder()}.
         */
        Builder() {
        }

        /**
         * Sets the stable logical identifier for the peer.
         *
         * @param nodeId non-null, non-blank node ID
         * @return this builder
         */
        public Builder nodeId(String nodeId) {
            this.nodeId = nodeId;
            return this;
        }

        /**
         * Sets the hostname or IP address of the peer.
         *
         * @param host non-null, non-blank host string
         * @return this builder
         */
        public Builder host(String host) {
            this.host = host;
            return this;
        }

        /**
         * Sets the TCP port.
         *
         * @param port port in [1024, 65535]
         * @return this builder
         */
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        /**
         * Sets the hex-encoded secp256k1 public key.
         *
         * @param publicKeyHex hex-encoded key; may be null
         * @return this builder
         */
        public Builder publicKeyHex(String publicKeyHex) {
            this.publicKeyHex = publicKeyHex;
            return this;
        }

        /**
         * Sets the last-seen timestamp.
         *
         * @param lastSeen UTC instant; may be null for new peers
         * @return this builder
         */
        public Builder lastSeen(Instant lastSeen) {
            this.lastSeen = lastSeen;
            return this;
        }

        /**
         * Builds and returns a {@code Peer}.
         *
         * @return new {@code Peer} instance
         * @throws NullPointerException     if nodeId or host is null
         * @throws IllegalArgumentException if nodeId or host is blank, or port is out of range
         */
        public Peer build() {
            return new Peer(nodeId, host, port, publicKeyHex, lastSeen);
        }
    }
}
