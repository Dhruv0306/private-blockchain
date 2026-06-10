/**
 * Assembly layer — the single location where all library modules are composed.
 *
 * <p>Following the Explicit Wiring principle (design.md §1), no hidden global state,
 * service-locator, or classpath scanning is used. Every dependency is supplied via
 * {@link com.privatechain.core.builder.BlockchainConfig#builder()}.</p>
 *
 * <p>Classes in this package:</p>
 * <ul>
 *   <li>{@link com.privatechain.core.builder.BlockchainConfig} — fluent builder and single
 *       assembly point for all subsystems; produces a configured
 *       {@link com.privatechain.core.builder.BlockchainNode} (FR-CFG-01).</li>
 *   <li>{@link com.privatechain.core.builder.BlockchainNode} — top-level entry point;
 *       manages the node lifecycle ({@code start()}/{@code stop()}) and exposes the
 *       primary public API (FR-CFG-01).</li>
 *   <li>{@link com.privatechain.core.builder.Blockchain} — chain manager responsible for
 *       block appending, integrity verification, and storage delegation (FR-CORE-04).</li>
 *   <li>{@link com.privatechain.core.builder.GenesisBlockFactory} — creates the deterministic
 *       genesis block whose {@code previousHash} is 64 hexadecimal zeros (FR-CORE-07).</li>
 * </ul>
 *
 * @since 1.0.0
 */
package com.privatechain.core.builder;
