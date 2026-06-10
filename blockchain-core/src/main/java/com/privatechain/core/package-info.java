/**
 * Top-level package for the private-blockchain core library.
 *
 * <p>This package contains the three sub-packages that define the library's public
 * contracts and the one assembly package where all modules are wired together:</p>
 * <ul>
 *   <li>{@code core.model} — immutable domain objects (Block, BlockHeader, Transaction)</li>
 *   <li>{@code core.spi}   — Service Provider Interfaces for consumers to implement</li>
 *   <li>{@code core.event} — sealed event hierarchy and the asynchronous event bus</li>
 *   <li>{@code core.builder} — BlockchainConfig, BlockchainNode, and Blockchain</li>
 * </ul>
 *
 * <p>{@code blockchain-core} has zero mandatory runtime dependencies beyond the JDK,
 * ensuring that any Java project can depend on it without risk of classpath conflicts
 * (design.md §7.1).</p>
 *
 * @since 1.0.0
 */
package com.privatechain.core;
