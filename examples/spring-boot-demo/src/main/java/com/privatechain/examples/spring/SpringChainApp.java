package com.privatechain.examples.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.privatechain.consensus.pow.ProofOfWorkEngine;
import com.privatechain.core.builder.BlockchainConfig;
import com.privatechain.core.builder.BlockchainNode;
import com.privatechain.storage.memory.InMemoryStorage;
import com.privatechain.wallet.Wallet;
import com.privatechain.wallet.WalletManager;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot entry point for the blockchain REST demo application.
 *
 * <p>This class demonstrates {@code blockchain-spring} autoconfiguration (AC-10):
 * a single {@code @SpringBootApplication} annotation plus {@code blockchain.*}
 * properties in {@code application.yml} is all that is needed to create, configure,
 * and start a {@link com.privatechain.core.builder.BlockchainNode} as a Spring bean.</p>
 *
 * <h2>Autoconfigured beans (from blockchain-spring)</h2>
 * <ul>
 *   <li>{@code BlockchainNode} — started automatically on application context refresh</li>
 *   <li>{@code TransactionMempool} — wired to the node lifecycle</li>
 *   <li>{@code BlockchainHealthIndicator} — exposed via Spring Boot Actuator</li>
 * </ul>
 *
 * <h2>Demo-specific beans (defined here)</h2>
 * <ul>
 *   <li>{@link #demoWallet()} — a randomly-generated wallet used by the REST controller
 *       to sign submitted transactions. In production, load from an encrypted keystore.</li>
 *   <li>{@link #objectMapper()} — registers {@link JavaTimeModule} so that
 *       {@code Instant} fields in {@code Block} and {@code NodeStatus} serialize as
 *       ISO-8601 strings, matching the format used by {@code BlockSerializer.MAPPER}.</li>
 * </ul>
 *
 * <h2>Run with Maven</h2>
 * <pre>{@code
 * mvn spring-boot:run -pl examples/spring-boot-demo
 * }</pre>
 *
 * <h2>Available endpoints</h2>
 * <ul>
 *   <li>{@code GET  /api/status}       — node status (height, mempool size, engine name)</li>
 *   <li>{@code GET  /api/chain}        — full block list as JSON array</li>
 *   <li>{@code GET  /api/chain/{idx}}  — single block by index</li>
 *   <li>{@code POST /api/transactions} — submit a payment transaction</li>
 *   <li>{@code POST /api/mine}         — manually trigger block mining</li>
 *   <li>{@code GET  /actuator/health}  — Spring Actuator health check</li>
 * </ul>
 *
 * @see BlockchainRestController
 * @see com.privatechain.spring.BlockchainAutoConfiguration
 * @since 1.0.0
 */
@SpringBootApplication
public class SpringChainApp {

    private BlockchainNode nodeInstance;

    /**
     * Application entry point.
     *
     * @param args command-line arguments (passed through to Spring)
     */
    public static void main(String[] args) {
        SpringApplication.run(SpringChainApp.class, args);
    }

    // ─── Demo beans ───────────────────────────────────────────────────────────

    /**
     * Creates, configures, and starts the BlockchainNode as a Spring bean.
     * Defined manually here because blockchain-spring autoconfiguration
     * requires the full network stack; this demo uses in-memory storage only.
     */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "This bean is intentionally mutable and exposed for injection into the REST controller, "
            + "and is stopped cleanly in the @PreDestroy method below.")
    @Bean
    public BlockchainNode blockchainNode() {
        nodeInstance = BlockchainConfig.builder()
            .chainId("spring-demo-chain")
            .consensusEngine(new ProofOfWorkEngine(2))
            .storage(new InMemoryStorage())
            .build();
        nodeInstance.start();
        System.out.println("[SpringChainApp] BlockchainNode started. "
            + "Chain height: " + nodeInstance.status().chainHeight());
        return nodeInstance;
    }

    /**
     * Stops the node cleanly when the Spring context shuts down.
     */
    @PreDestroy
    public void stopNode() {
        if (nodeInstance != null) {
            nodeInstance.stop();
            System.out.println("[SpringChainApp] BlockchainNode stopped.");
        }
    }

    /**
     * Provides a freshly generated demo wallet for signing REST-submitted transactions.
     *
     * <p>The wallet's private key is generated at startup using {@link WalletManager}
     * and is never persisted or logged (NFR-SEC-01). The wallet's address is printed
     * to the application log at {@code INFO} level so operators know which signer is
     * active for the demo session.</p>
     *
     * <p>In a production application, replace this bean with one that loads from an
     * encrypted keystore via {@link WalletManager#exportKeystore(Wallet, String)} /
     * {@code WalletManager.importKeystore(String, String)}.</p>
     *
     * @return a new, randomly generated {@link Wallet}
     */
    @Bean
    public Wallet demoWallet() {
        WalletManager wm = new WalletManager();
        Wallet wallet = wm.createWallet();
        // Log the wallet address (not the private key) for operator visibility
        System.out.println("[SpringChainApp] Demo wallet address: " + wallet.getAddress());
        return wallet;
    }

    /**
     * Configures Spring MVC's Jackson {@link ObjectMapper} so that
     * {@code java.time.Instant} fields serialize as ISO-8601 strings
     * (e.g. {@code "2026-06-06T10:00:00Z"}) instead of numeric timestamp arrays.
     *
     * <p>Spring Boot 4 removed the {@code Jackson2ObjectMapperBuilderCustomizer}
     * infrastructure. Declaring an {@link ObjectMapper} bean directly is the
     * supported replacement and gives full control over serialization behavior.</p>
     *
     * <p>This matches the format produced by {@code BlockSerializer.MAPPER} used
     * internally by all storage backends, keeping REST output consistent with
     * persisted JSON payloads.</p>
     *
     * @return a fully configured {@link ObjectMapper}
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
