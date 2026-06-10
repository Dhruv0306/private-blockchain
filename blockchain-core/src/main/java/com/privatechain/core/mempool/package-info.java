/**
 * In-memory transaction pool with pluggable prioritization and TTL eviction.
 *
 * <p>The mempool sits between transaction submission and block mining. It validates
 * incoming transactions, prevents duplicates, and orders pending transactions for
 * mining using the configured {@link com.privatechain.core.spi.TransactionPrioritizer}.</p>
 *
 * <p>Key class:</p>
 * <ul>
 *   <li>{@link com.privatechain.core.mempool.TransactionMempool} — validates on submit,
 *       rejects duplicates, and schedules TTL eviction via a daemon
 *       {@code ScheduledExecutorService}. Confirmed transactions are automatically removed
 *       when a {@link com.privatechain.core.event.BlockchainEvent.BlockAddedEvent} is
 *       received from the event bus (FR-MEMPOOL-01, FR-MEMPOOL-05).</li>
 * </ul>
 *
 * @since 1.0.0
 */
package com.privatechain.core.mempool;
