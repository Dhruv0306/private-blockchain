/**
 * Allowlist enforcement — gates every inbound network message against a persisted
 * set of permitted node identifiers (FR-AC-01).
 *
 * <p>Classes in this package:</p>
 * <ul>
 *   <li>{@link com.privatechain.access.allowlist.AllowlistManager} — in-memory
 *       {@code Set}-backed manager; checked before any message is dispatched to a
 *       handler. Non-allowlisted peers are silently dropped and logged (AC-07).</li>
 *   <li>{@link com.privatechain.access.allowlist.AllowlistStore} — persists the
 *       allowlist entries to {@link com.privatechain.core.spi.BlockchainStorage}
 *       so the permitted set survives node restarts.</li>
 * </ul>
 *
 * @since 1.0.0
 */
package com.privatechain.access.allowlist;
