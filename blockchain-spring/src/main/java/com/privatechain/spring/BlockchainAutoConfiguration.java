package com.privatechain.spring;

import com.privatechain.consensus.pow.ProofOfWorkEngine;
import com.privatechain.core.builder.BlockchainConfig;
import com.privatechain.core.builder.BlockchainNode;
import com.privatechain.core.event.BlockchainEventListener;
import com.privatechain.core.spi.BlockchainStorage;
import com.privatechain.core.spi.ConsensusEngine;
import com.privatechain.core.spi.TransactionValidator;
import com.privatechain.storage.memory.InMemoryStorage;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot autoconfiguration for the private-blockchain library.
 *
 * <p>This class is the single assembly point for all blockchain Spring beans,
 * mirroring the non-Spring assembly model in {@link BlockchainConfig} but
 * adapting it to dependency injection (FR-CFG-01, FR-CFG-03).</p>
 *
 * <h2>Activation</h2>
 * <p>Active by default. Disable completely with:</p>
 * <pre>{@code
 * blockchain:
 *   enabled: false
 * }</pre>
 *
 * <h2>Zero-configuration start (AC-10)</h2>
 * <p>No {@code blockchain.*} properties required. Defaults:
 * {@link ProofOfWorkEngine} (difficulty 4) + {@link InMemoryStorage}.</p>
 *
 * <h2>Override points (T-072)</h2>
 * <p>Every bean is guarded by {@code @ConditionalOnMissingBean}. Provide any
 * of the following in your {@code @Configuration} to replace the default:</p>
 * <ul>
 *   <li>{@link BlockchainNode} — replaces the entire node</li>
 *   <li>{@link ConsensusEngine} — replaces {@code ProofOfWorkEngine}</li>
 *   <li>{@link BlockchainStorage} — replaces {@code InMemoryStorage}</li>
 *   <li>{@link BlockchainHealthIndicator} — replaces the status bean</li>
 * </ul>
 *
 * <h2>Multiple validators and listeners</h2>
 * <p>All {@link TransactionValidator} and {@link BlockchainEventListener} beans
 * are discovered and registered automatically in declaration order.</p>
 *
 * <h2>Node lifecycle</h2>
 * <p>The {@code BlockchainNode} bean uses {@code initMethod = "start"} and
 * {@code destroyMethod = "stop"}. Spring calls {@code start()} after all beans
 * are wired and {@code stop()} on context close.</p>
 *
 * <h2>Actuator integration</h2>
 * <p>{@link BlockchainHealthIndicator} is a plain status-provider bean with no
 * compile-time dependency on {@code spring-boot-actuator}. To wire it into the
 * Actuator health endpoint, add this to your {@code @Configuration}:</p>
 * <pre>{@code
 * @Bean
 * HealthIndicator blockchainHealth(BlockchainHealthIndicator indicator) {
 *     return () -> indicator.isUp()
 *         ? Health.up().withDetails(indicator.getDetails()).build()
 *         : Health.down().withDetails(indicator.getDetails()).build();
 * }
 * }</pre>
 *
 * @see BlockchainProperties
 * @see BlockchainHealthIndicator
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(BlockchainProperties.class)
@ConditionalOnProperty(
    prefix = "blockchain",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class BlockchainAutoConfiguration {

    // ─── Primary node bean ────────────────────────────────────────────────────

    /**
     * Creates and wires the {@link BlockchainNode} bean from bound properties and
     * any user-provided override beans (T-070, T-072).
     *
     * <h4>Dependency resolution order</h4>
     * <ol>
     *   <li>{@link ConsensusEngine} — user bean if present; else
     *       {@link ProofOfWorkEngine} with configured difficulty.</li>
     *   <li>{@link BlockchainStorage} — user bean if present; else
     *       {@link InMemoryStorage} (ephemeral, for dev/demo).</li>
     *   <li>{@link TransactionValidator} — all beans, chained in order (FR-TX-03).</li>
     *   <li>{@link BlockchainEventListener} — all beans, registered in order
     *       (FR-EVENT-02).</li>
     * </ol>
     *
     * <p>The node is not started here. Spring calls {@code start()} via
     * {@code initMethod} after all beans are fully wired.</p>
     *
     * @param properties        bound {@code blockchain.*} properties
     * @param engineProvider    optional {@link ConsensusEngine} override
     * @param storageProvider   optional {@link BlockchainStorage} override
     * @param validatorProvider zero or more {@link TransactionValidator} beans
     * @param listenerProvider  zero or more {@link BlockchainEventListener} beans
     * @return configured, not-yet-started {@link BlockchainNode}
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnMissingBean
    public BlockchainNode blockchainNode(
        BlockchainProperties properties,
        ObjectProvider<ConsensusEngine> engineProvider,
        ObjectProvider<BlockchainStorage> storageProvider,
        ObjectProvider<TransactionValidator> validatorProvider,
        ObjectProvider<BlockchainEventListener> listenerProvider) {

        BlockchainConfig.Builder builder = BlockchainConfig.builder()
            .chainId(properties.getChainId())
            .networkPort(properties.getNetworkPort())
            .blockTimeSeconds(properties.getBlockTimeSeconds())
            .difficulty(properties.getDifficulty())
            .maxPeers(properties.getMaxPeers())
            .mempoolTtl(properties.getMempool().getTtl())
            .maxMempoolSize(properties.getMempool().getMaxSize());

        // Consensus: user bean wins; otherwise PoW with configured difficulty
        ConsensusEngine engine = engineProvider.getIfAvailable(
            () -> new ProofOfWorkEngine(properties.getDifficulty()));
        builder.consensusEngine(engine);

        // Storage: user bean wins; otherwise InMemory (ephemeral)
        BlockchainStorage storage = storageProvider.getIfAvailable(InMemoryStorage::new);
        builder.storage(storage);

        // Chain all user-declared validators (FR-TX-03)
        validatorProvider.orderedStream().forEach(builder::transactionValidator);

        // Register all user-declared event listeners (FR-EVENT-02)
        listenerProvider.orderedStream().forEach(builder::eventListener);

        return builder.build();
    }

    // ─── Health status bean ───────────────────────────────────────────────────

    /**
     * Creates the {@link BlockchainHealthIndicator} status bean.
     *
     * <p>This is a plain POJO — it does not implement
     * {@code spring-boot-actuator}'s {@code HealthIndicator} interface, keeping
     * this module free of a mandatory Actuator dependency. See
     * {@link BlockchainHealthIndicator} for instructions on bridging it to
     * Actuator's health endpoint (T-070).</p>
     *
     * <p>Guarded by {@code @ConditionalOnMissingBean} so applications can replace
     * it without touching library sources (T-072).</p>
     *
     * @param node the autoconfigured or user-provided {@link BlockchainNode}
     * @return status provider for the running node
     */
    @Bean
    @ConditionalOnMissingBean
    public BlockchainHealthIndicator blockchainHealthIndicator(BlockchainNode node) {
        return new BlockchainHealthIndicator(node);
    }
}
