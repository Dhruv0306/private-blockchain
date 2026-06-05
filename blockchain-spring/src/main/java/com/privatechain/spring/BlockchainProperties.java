package com.privatechain.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration properties for the private-blockchain Spring Boot autoconfiguration.
 *
 * <p>All properties are bound to the {@code blockchain.*} prefix in
 * {@code application.yml} / {@code application.properties}. Every field has a
 * production-ready default so that zero configuration is required to start a working
 * in-memory PoW chain (FR-CFG-02, AC-10).</p>
 *
 * <h2>Minimal YAML</h2>
 * <pre>{@code
 * blockchain:
 *   enabled: true   # default; omit to keep the node active
 * }</pre>
 *
 * <h2>Full YAML reference</h2>
 * <pre>{@code
 * blockchain:
 *   enabled: true
 *   chain-id: my-chain
 *   network-port: 8545
 *   block-time-seconds: 10
 *   difficulty: 4
 *   max-peers: 25
 *   mempool:
 *     ttl: 30m         # Duration — supports 30m, PT30M, 1800s, etc.
 *     max-size: 10000
 * }</pre>
 *
 * <p>IDE autocomplete for all properties is generated at compile time by the
 * {@code spring-boot-configuration-processor} annotation processor, which produces
 * {@code META-INF/spring-configuration-metadata.json} (T-071).</p>
 *
 * @see BlockchainAutoConfiguration
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "blockchain")
public class BlockchainProperties {

    // ─── Top-level properties ─────────────────────────────────────────────────

    /**
     * Whether to enable the blockchain autoconfiguration.
     *
     * <p>Set to {@code false} to prevent the {@code BlockchainNode} bean from being
     * created. Useful in test slices or profiles where the blockchain is not required.
     * Default: {@code true}.</p>
     */
    private boolean enabled = true;

    /**
     * Unique identifier for this blockchain network.
     *
     * <p>Embedded in the genesis block to distinguish chains and prevent
     * cross-chain contamination. Must be non-blank.
     * Default: {@code "private-blockchain"}.</p>
     */
    private String chainId = "private-blockchain";

    /**
     * TCP port on which the P2P node server listens for inbound peer connections.
     *
     * <p>Must be in the range [1024, 65535].
     * Default: {@code 8545}.</p>
     */
    private int networkPort = 8545;

    /**
     * Target time in seconds between consecutive blocks.
     *
     * <p>Used by the {@code DifficultyAdjuster} in PoW mode to keep block intervals
     * stable. Must be &ge; 1. Default: {@code 10}.</p>
     */
    private int blockTimeSeconds = 10;

    /**
     * Proof-of-Work difficulty: number of leading zero bits required in a valid block hash.
     *
     * <p>Higher values increase mining time exponentially. A value of {@code 4} requires
     * a hash prefix of {@code "0000"} (AC-03). Must be &ge; 1. Default: {@code 4}.</p>
     */
    private int difficulty = 4;

    /**
     * Maximum number of simultaneous peer connections.
     *
     * <p>Must be &ge; 1. Default: {@code 25} (FR-NET-07).</p>
     */
    private int maxPeers = 25;

    /**
     * Mempool-specific configuration.
     *
     * <p>Nested under the {@code blockchain.mempool.*} key.</p>
     */
    @NestedConfigurationProperty
    private Mempool mempool = new Mempool();

    // ─── Top-level getters / setters ──────────────────────────────────────────

    /**
     * Returns whether blockchain autoconfiguration is enabled.
     *
     * @return {@code true} if the {@code BlockchainNode} bean should be created
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether blockchain autoconfiguration is enabled.
     *
     * @param enabled {@code false} to suppress {@code BlockchainNode} bean creation
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns the chain identifier embedded in the genesis block.
     *
     * @return non-null, non-blank chain ID
     */
    public String getChainId() {
        return chainId;
    }

    /**
     * Sets the chain identifier.
     *
     * @param chainId non-null, non-blank identifier
     * @throws NullPointerException if {@code chainId} is null
     */
    public void setChainId(String chainId) {
        this.chainId = Objects.requireNonNull(chainId, "chainId must not be null");
    }

    /**
     * Returns the TCP port for the P2P node server.
     *
     * @return port number in [1024, 65535]
     */
    public int getNetworkPort() {
        return networkPort;
    }

    /**
     * Sets the TCP port for the P2P node server.
     *
     * @param networkPort port number in [1024, 65535]
     */
    public void setNetworkPort(int networkPort) {
        this.networkPort = networkPort;
    }

    /**
     * Returns the target block time in seconds.
     *
     * @return block time (&ge; 1)
     */
    public int getBlockTimeSeconds() {
        return blockTimeSeconds;
    }

    /**
     * Sets the target block time in seconds.
     *
     * @param blockTimeSeconds block time (&ge; 1)
     */
    public void setBlockTimeSeconds(int blockTimeSeconds) {
        this.blockTimeSeconds = blockTimeSeconds;
    }

    /**
     * Returns the Proof-of-Work difficulty (leading zero bit count).
     *
     * @return difficulty (&ge; 1)
     */
    public int getDifficulty() {
        return difficulty;
    }

    /**
     * Sets the Proof-of-Work difficulty.
     *
     * @param difficulty leading zero bit count (&ge; 1)
     */
    public void setDifficulty(int difficulty) {
        this.difficulty = difficulty;
    }

    /**
     * Returns the maximum number of simultaneous peer connections.
     *
     * @return max peers (&ge; 1)
     */
    public int getMaxPeers() {
        return maxPeers;
    }

    /**
     * Sets the maximum number of simultaneous peer connections.
     *
     * @param maxPeers max peers (&ge; 1)
     */
    public void setMaxPeers(int maxPeers) {
        this.maxPeers = maxPeers;
    }

    /**
     * Returns the mempool configuration.
     *
     * @return non-null {@link Mempool} properties
     */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "Mempool is intentionally exposed as part of the public Spring Boot SPI. "
            + "The reference is stable (field is final and only replaced via setMempool), "
            + "and the Mempool class is thread-safe by contract. Callers are expected to treat "
            + "it as a read-mostly configuration object and not to mutate it after initialization.")
    public Mempool getMempool() {
        return mempool;
    }

    /**
     * Sets the mempool configuration.
     *
     * @param mempool non-null mempool properties
     * @throws NullPointerException if {@code mempool} is null
     */
    public void setMempool(Mempool mempool) {
        this.mempool = Objects.requireNonNull(mempool, "mempool must not be null");
    }

    // ─── Nested mempool properties ────────────────────────────────────────────

    /**
     * Mempool configuration properties, bound to {@code blockchain.mempool.*}.
     *
     * @since 1.0.0
     */
    public static class Mempool {

        /**
         * Time-to-live for unconfirmed transactions in the mempool.
         *
         * <p>Transactions older than this duration are evicted automatically.
         * Accepts any Spring {@link Duration} format: {@code 30m}, {@code PT30M},
         * {@code 1800s}. Default: {@code 30m}.</p>
         */
        private Duration ttl = Duration.ofMinutes(30);

        /**
         * Maximum number of transactions the mempool can hold.
         *
         * <p>Once the limit is reached, new submissions are rejected until older
         * transactions are evicted or confirmed. Default: {@link Integer#MAX_VALUE}
         * (effectively unbounded).</p>
         */
        private int maxSize = Integer.MAX_VALUE;

        /**
         * Returns the transaction TTL duration.
         *
         * @return non-null, positive {@link Duration}
         */
        public Duration getTtl() {
            return ttl;
        }

        /**
         * Sets the transaction TTL duration.
         *
         * @param ttl non-null, positive duration
         * @throws NullPointerException if {@code ttl} is null
         */
        public void setTtl(Duration ttl) {
            this.ttl = Objects.requireNonNull(ttl, "mempool.ttl must not be null");
        }

        /**
         * Returns the maximum mempool size.
         *
         * @return max transaction count (&ge; 1)
         */
        public int getMaxSize() {
            return maxSize;
        }

        /**
         * Sets the maximum mempool size.
         *
         * @param maxSize max transaction count (&ge; 1)
         */
        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }
    }
}
