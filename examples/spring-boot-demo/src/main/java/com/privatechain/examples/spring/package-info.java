/**
 * Spring Boot REST demo — exposes a running
 * {@link com.privatechain.core.builder.BlockchainNode} as an HTTP API using
 * Spring Boot 4.x autoconfiguration (T-075, AC-10).
 *
 * <p>Classes in this package:</p>
 * <ul>
 *   <li>{@link com.privatechain.examples.spring.SpringChainApp} — the
 *       {@code @SpringBootApplication} entry point. Defines a
 *       {@link com.privatechain.core.builder.BlockchainNode} bean manually (started
 *       and stopped via {@code @PreDestroy}), a demo
 *       {@link com.privatechain.wallet.Wallet} bean for signing submitted
 *       transactions, and a {@code Jackson2ObjectMapperBuilder} bean that registers
 *       {@code JavaTimeModule} for ISO-8601 {@code Instant} serialisation.</li>
 *   <li>{@link com.privatechain.examples.spring.BlockchainRestController} — five
 *       REST endpoints:
 *       <ul>
 *         <li>{@code GET  /api/status}      — live {@code NodeStatus} snapshot</li>
 *         <li>{@code GET  /api/chain}        — full block list as JSON array</li>
 *         <li>{@code GET  /api/chain/{idx}}  — single block by index</li>
 *         <li>{@code POST /api/transactions} — sign and submit a payment</li>
 *         <li>{@code POST /api/mine}         — mine a block from the mempool</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <p>Run with:</p>
 * <pre>{@code
 * mvn spring-boot:run -pl examples/spring-boot-demo
 * }</pre>
 *
 * @since 1.0.0
 */
package com.privatechain.examples.spring;
