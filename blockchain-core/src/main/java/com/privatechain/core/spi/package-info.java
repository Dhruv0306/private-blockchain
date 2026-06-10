/**
 * Service Provider Interfaces (SPIs) — the primary extension points of the library.
 *
 * <p>Consumers implement these interfaces to plug in their own behaviour:</p>
 * <ul>
 *   <li>{@link com.privatechain.core.spi.ConsensusEngine} — validates and produces blocks;
 *       built-in implementations are in {@code blockchain-consensus} (FR-CONS-01).</li>
 *   <li>{@link com.privatechain.core.spi.TransactionValidator} — validates transactions
 *       before they enter the mempool; chain-of-responsibility composable (FR-TX-01).</li>
 *   <li>{@link com.privatechain.core.spi.BlockchainStorage} — persists and retrieves blocks;
 *       built-in implementations are in {@code blockchain-storage} (FR-STOR-01).</li>
 *   <li>{@link com.privatechain.core.spi.TransactionPrioritizer} — orders transactions in
 *       the mempool; built-in implementations are in {@code blockchain-core} (FR-MEMPOOL-02).</li>
 *   <li>{@link com.privatechain.core.spi.ValidationResult} — immutable result of a validator
 *       run, carrying a status enum and a list of error messages (FR-TX-02).</li>
 * </ul>
 *
 * @since 1.0.0
 */
package com.privatechain.core.spi;
