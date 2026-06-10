/**
 * Spring Boot autoconfiguration for the private-blockchain library (FR-CFG-03, AC-10).
 *
 * <p>Adding {@code blockchain-spring} to an application's classpath and setting
 * {@code blockchain.enabled=true} in {@code application.yml} is sufficient to
 * create, configure, and start a fully operational
 * {@link com.privatechain.core.builder.BlockchainNode} as a managed Spring bean —
 * no Java configuration class is required.</p>
 *
 * <p>Classes in this package:</p>
 * <ul>
 *   <li>{@link com.privatechain.spring.BlockchainAutoConfiguration} — the
 *       {@code @AutoConfiguration} entry point. Guarded by
 *       {@code @ConditionalOnProperty("blockchain.enabled")}. Creates
 *       {@code BlockchainNode} and {@code TransactionMempool} beans, each
 *       overridable via {@code @ConditionalOnMissingBean}.</li>
 *   <li>{@link com.privatechain.spring.BlockchainProperties} — binds all
 *       {@code blockchain.*} properties from {@code application.yml} with full
 *       IDE autocomplete support (generated metadata). Includes a nested
 *       {@code Mempool} section for TTL and pool-size settings.</li>
 *   <li>{@link com.privatechain.spring.BlockchainHealthIndicator} — exposes
 *       chain height and mempool size via Spring Boot Actuator's
 *       {@code /actuator/health} endpoint without introducing a mandatory
 *       compile-time Actuator dependency.</li>
 * </ul>
 *
 * <p>Autoconfiguration is registered in:<br>
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * for Spring Boot 4.x discovery.</p>
 *
 * @since 1.0.0
 */
package com.privatechain.spring;
