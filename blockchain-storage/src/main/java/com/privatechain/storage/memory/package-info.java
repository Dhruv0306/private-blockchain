/**
 * In-memory storage backend — ephemeral, zero-dependency block storage backed by
 * a {@link java.util.LinkedHashMap} (FR-STOR-02).
 *
 * <p>{@link com.privatechain.storage.memory.InMemoryStorage} is the default storage
 * used by {@link com.privatechain.core.builder.BlockchainConfig#builder()} when no
 * explicit backend is configured. It is suitable for unit tests, integration tests,
 * and runnable demos where persistence across JVM restarts is not required.</p>
 *
 * <p>Thread safety: all reads use a shared {@code ReadLock}; writes use an exclusive
 * {@code WriteLock} via a {@link java.util.concurrent.locks.ReentrantReadWriteLock}
 * (FR-STOR-06).</p>
 *
 * @since 1.0.0
 */
package com.privatechain.storage.memory;
