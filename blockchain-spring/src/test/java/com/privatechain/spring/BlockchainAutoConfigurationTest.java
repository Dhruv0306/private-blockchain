package com.privatechain.spring;

import com.privatechain.consensus.roundrobin.RoundRobinEngine;
import com.privatechain.core.builder.BlockchainConfig;
import com.privatechain.core.builder.BlockchainNode;
import com.privatechain.core.spi.ConsensusEngine;
import com.privatechain.storage.memory.InMemoryStorage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Spring Boot integration tests for {@link BlockchainAutoConfiguration} (T-073).
 *
 * <p>Uses {@link ApplicationContextRunner} — the standard Spring Boot utility for
 * autoconfiguration slice tests. Each {@code .run()} call creates an isolated,
 * fully refreshed context and closes it automatically, so the
 * {@link BlockchainNode} lifecycle ({@code start} / {@code stop}) is exercised
 * cleanly without cross-test state leakage.</p>
 *
 * <h2>Coverage targets</h2>
 * <ul>
 *   <li>Default bean creation with zero configuration (AC-10)</li>
 *   <li>Node is started (genesis block present) after context refresh</li>
 *   <li>Disabling via {@code blockchain.enabled=false}</li>
 *   <li>Custom {@link ConsensusEngine} bean replaces PoW default (T-072)</li>
 *   <li>Custom {@link BlockchainNode} bean suppresses autoconfiguration (T-072)</li>
 *   <li>All {@code blockchain.*} property bindings (T-071)</li>
 *   <li>Default property values (FR-CFG-02)</li>
 *   <li>Health indicator bean created and returns correct details</li>
 *   <li>Health indicator DOWN when node not started</li>
 *   <li>{@link BlockchainProperties} setter / getter coverage (JaCoCo threshold)</li>
 * </ul>
 *
 * @see BlockchainAutoConfiguration
 * @see BlockchainProperties
 * @see BlockchainHealthIndicator
 * @since 1.0.0
 */
@DisplayName("BlockchainAutoConfiguration integration tests")
class BlockchainAutoConfigurationTest {

    // ─── Shared runner ────────────────────────────────────────────────────────

    /**
     * Base runner preloaded with {@link BlockchainAutoConfiguration}.
     * Tests chain {@code .withPropertyValues()} or {@code .withUserConfiguration()}
     * before calling {@code .run()}.
     */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(BlockchainAutoConfiguration.class));

    // ─── T-073: Default bean creation ────────────────────────────────────────

    /**
     * AC-10: a {@code BlockchainNode} bean must exist with zero configuration.
     */
    @Test
    @DisplayName("BlockchainNode bean created with zero configuration (AC-10)")
    void blockchainNodeBeanCreatedByDefault() {
        contextRunner.run(context ->
            assertThat(context).hasSingleBean(BlockchainNode.class));
    }

    /**
     * The node must be in a started state after context refresh
     * ({@code initMethod = "start"} was called).
     * {@link BlockchainNode#status()} throws if not started.
     */
    @Test
    @DisplayName("BlockchainNode is started after context refresh")
    void blockchainNodeIsStartedAfterContextRefresh() {
        contextRunner.run(context -> {
            BlockchainNode node = context.getBean(BlockchainNode.class);

            assertThatNoException().isThrownBy(node::status);

            // genesis block must be present
            assertThat(node.status().chainHeight()).isGreaterThanOrEqualTo(1);
        });
    }

    // ─── T-070: @ConditionalOnProperty ───────────────────────────────────────

    /**
     * Setting {@code blockchain.enabled=false} must suppress all beans.
     */
    @Test
    @DisplayName("No beans when blockchain.enabled=false")
    void beansAbsentWhenDisabled() {
        contextRunner
            .withPropertyValues("blockchain.enabled=false")
            .run(context -> {
                assertThat(context).doesNotHaveBean(BlockchainNode.class);
                assertThat(context).doesNotHaveBean(BlockchainHealthIndicator.class);
            });
    }

    /**
     * Omitting {@code blockchain.enabled} is treated as {@code true}.
     */
    @Test
    @DisplayName("BlockchainNode created when blockchain.enabled absent (matchIfMissing=true)")
    void blockchainNodeCreatedWhenPropertyAbsent() {
        contextRunner.run(context ->
            assertThat(context).hasSingleBean(BlockchainNode.class));
    }

    // ─── T-072: @ConditionalOnMissingBean overrides ───────────────────────────

    /**
     * A user {@link ConsensusEngine} bean replaces the PoW default.
     * Verified via {@code NodeStatus.consensusEngine()} which returns the engine name.
     */
    @Test
    @DisplayName("User ConsensusEngine bean replaces PoW default (T-072)")
    void customConsensusEngineIsUsed() {
        contextRunner
            .withUserConfiguration(CustomEngineConfig.class)
            .run(context -> {
                assertThat(context).hasSingleBean(BlockchainNode.class);
                String engine = context.getBean(BlockchainNode.class)
                    .status().consensusEngine();
                assertThat(engine).isEqualTo("RoundRobin");
            });
    }

    /**
     * A user-provided {@link BlockchainNode} suppresses autoconfiguration.
     */
    @Test
    @DisplayName("User BlockchainNode bean suppresses autoconfiguration (T-072)")
    void customBlockchainNodeSuppressesAutoConfig() {
        contextRunner
            .withUserConfiguration(CustomNodeConfig.class)
            .run(context -> {
                assertThat(context).hasSingleBean(BlockchainNode.class);
                assertThat(context.containsBean("customNode")).isTrue();
                assertThat(context.containsBean("blockchainNode")).isFalse();
            });
    }

    // ─── T-071: Property binding ──────────────────────────────────────────────

    /**
     * All {@code blockchain.*} properties must bind to their fields.
     */
    @Test
    @DisplayName("All blockchain.* properties bind correctly (T-071)")
    void propertiesBindCorrectly() {
        contextRunner
            .withPropertyValues(
                "blockchain.chain-id=test-chain",
                "blockchain.network-port=9001",
                "blockchain.block-time-seconds=5",
                "blockchain.difficulty=2",
                "blockchain.max-peers=10",
                "blockchain.mempool.ttl=10m",
                "blockchain.mempool.max-size=500")
            .run(context -> {
                BlockchainProperties p = context.getBean(BlockchainProperties.class);
                assertThat(p.getChainId()).isEqualTo("test-chain");
                assertThat(p.getNetworkPort()).isEqualTo(9001);
                assertThat(p.getBlockTimeSeconds()).isEqualTo(5);
                assertThat(p.getDifficulty()).isEqualTo(2);
                assertThat(p.getMaxPeers()).isEqualTo(10);
                assertThat(p.getMempool().getTtl()).isEqualTo(Duration.ofMinutes(10));
                assertThat(p.getMempool().getMaxSize()).isEqualTo(500);
            });
    }

    /**
     * Default property values must match documented production-ready values (FR-CFG-02).
     */
    @Test
    @DisplayName("Default property values are production-ready (FR-CFG-02)")
    void defaultPropertyValuesAreProductionReady() {
        contextRunner.run(context -> {
            BlockchainProperties p = context.getBean(BlockchainProperties.class);
            assertThat(p.isEnabled()).isTrue();
            assertThat(p.getChainId()).isEqualTo("private-blockchain");
            assertThat(p.getNetworkPort()).isEqualTo(8545);
            assertThat(p.getBlockTimeSeconds()).isEqualTo(10);
            assertThat(p.getDifficulty()).isEqualTo(4);
            assertThat(p.getMaxPeers()).isEqualTo(25);
            assertThat(p.getMempool().getTtl()).isEqualTo(Duration.ofMinutes(30));
            assertThat(p.getMempool().getMaxSize()).isEqualTo(Integer.MAX_VALUE);
        });
    }

    // ─── Health indicator ─────────────────────────────────────────────────────

    /**
     * {@link BlockchainHealthIndicator} bean must be created by default.
     */
    @Test
    @DisplayName("BlockchainHealthIndicator bean created (T-070)")
    void healthIndicatorBeanCreated() {
        contextRunner.run(context ->
            assertThat(context).hasSingleBean(BlockchainHealthIndicator.class));
    }

    /**
     * {@link BlockchainHealthIndicator#isUp()} must return {@code true} after start.
     */
    @Test
    @DisplayName("isUp() returns true after node start")
    void healthIndicatorReportsUp() {
        contextRunner.run(context -> {
            BlockchainHealthIndicator indicator =
                context.getBean(BlockchainHealthIndicator.class);
            assertThat(indicator.isUp()).isTrue();
        });
    }

    /**
     * {@link BlockchainHealthIndicator#getDetails()} must include all required keys.
     */
    @Test
    @DisplayName("getDetails() contains all health keys after node start")
    void healthIndicatorDetailsContainRequiredKeys() {
        contextRunner.run(context -> {
            Map<String, Object> details =
                context.getBean(BlockchainHealthIndicator.class).getDetails();

            assertThat(details)
                .containsKey(BlockchainHealthIndicator.KEY_STATUS)
                .containsKey(BlockchainHealthIndicator.KEY_CHAIN_HEIGHT)
                .containsKey(BlockchainHealthIndicator.KEY_MEMPOOL_SIZE)
                .containsKey(BlockchainHealthIndicator.KEY_PEER_COUNT)
                .containsKey(BlockchainHealthIndicator.KEY_ENGINE)
                .containsKey(BlockchainHealthIndicator.KEY_LAST_BLOCK);

            assertThat(details.get(BlockchainHealthIndicator.KEY_STATUS)).isEqualTo("UP");
            assertThat((Integer) details.get(BlockchainHealthIndicator.KEY_CHAIN_HEIGHT))
                .isGreaterThanOrEqualTo(1);
        });
    }

    /**
     * When the node has not been started, {@link BlockchainHealthIndicator#isUp()}
     * must return {@code false} and the detail map must carry {@code "DOWN"}.
     */
    @Test
    @DisplayName("isUp() returns false and details show DOWN when node not started")
    void healthIndicatorReportsDownWhenNodeNotStarted() {
        BlockchainNode unstarted = BlockchainConfig.builder().build();
        BlockchainHealthIndicator indicator = new BlockchainHealthIndicator(unstarted);

        assertThat(indicator.isUp()).isFalse();
        assertThat(indicator.getDetails().get(BlockchainHealthIndicator.KEY_STATUS))
            .isEqualTo("DOWN");
        assertThat(indicator.getDetails()).containsKey(BlockchainHealthIndicator.KEY_REASON);
    }

    // ─── BlockchainProperties setter / getter coverage ────────────────────────

    /**
     * Exercises all setters and the nested {@link BlockchainProperties.Mempool}
     * class to reach the 80 % JaCoCo line-coverage threshold.
     */
    @Test
    @DisplayName("BlockchainProperties setters and Mempool class are fully exercised")
    void blockchainPropertiesSettersExercised() {
        BlockchainProperties props = new BlockchainProperties();

        props.setEnabled(false);
        assertThat(props.isEnabled()).isFalse();

        props.setChainId("my-chain");
        assertThat(props.getChainId()).isEqualTo("my-chain");

        props.setNetworkPort(9999);
        assertThat(props.getNetworkPort()).isEqualTo(9999);

        props.setBlockTimeSeconds(30);
        assertThat(props.getBlockTimeSeconds()).isEqualTo(30);

        props.setDifficulty(8);
        assertThat(props.getDifficulty()).isEqualTo(8);

        props.setMaxPeers(50);
        assertThat(props.getMaxPeers()).isEqualTo(50);

        BlockchainProperties.Mempool mempool = new BlockchainProperties.Mempool();
        mempool.setTtl(Duration.ofHours(1));
        mempool.setMaxSize(2000);
        assertThat(mempool.getTtl()).isEqualTo(Duration.ofHours(1));
        assertThat(mempool.getMaxSize()).isEqualTo(2000);

        props.setMempool(mempool);
        assertThat(props.getMempool()).isSameAs(mempool);
    }

    // ─── Test configuration stubs ─────────────────────────────────────────────

    /**
     * Provides a {@link RoundRobinEngine} bean to verify the PoW default is
     * replaced by the user-declared engine (T-072).
     */
    @Configuration(proxyBeanMethods = false)
    static class CustomEngineConfig {

        /**
         * Returns a three-node {@link RoundRobinEngine}.
         *
         * @return non-null {@link ConsensusEngine}
         */
        @Bean
        public ConsensusEngine roundRobinEngine() {
            return new RoundRobinEngine(List.of("node-1", "node-2", "node-3"));
        }
    }

    /**
     * Provides a fully custom {@link BlockchainNode} bean to verify that
     * {@code @ConditionalOnMissingBean} suppresses the autoconfigured one (T-072).
     */
    @Configuration(proxyBeanMethods = false)
    static class CustomNodeConfig {

        /**
         * Creates a minimal node with a distinct {@code chainId}.
         * Lifecycle is managed by Spring via {@code initMethod} / {@code destroyMethod}.
         *
         * @return non-null, not-yet-started {@link BlockchainNode}
         */
        @Bean(initMethod = "start", destroyMethod = "stop")
        public BlockchainNode customNode() {
            return BlockchainConfig.builder()
                .storage(new InMemoryStorage())
                .chainId("custom-node-chain")
                .build();
        }
    }
}
