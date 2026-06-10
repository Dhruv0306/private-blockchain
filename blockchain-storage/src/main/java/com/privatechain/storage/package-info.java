/**
 * {@link com.privatechain.core.spi.BlockchainStorage} implementations and chain
 * export utilities for the private-blockchain library.
 *
 * <p>Four persistence backends are provided:</p>
 * <ul>
 *   <li>{@link com.privatechain.storage.memory} — ephemeral HashMap-backed storage
 *       for tests and demos (FR-STOR-02).</li>
 *   <li>{@link com.privatechain.storage.leveldb} — crash-safe persistent storage
 *       via LevelDB JNI (FR-STOR-03).</li>
 *   <li>{@link com.privatechain.storage.rocksdb} — high-throughput persistent
 *       storage via RocksDB JNI (FR-STOR-04).</li>
 *   <li>{@link com.privatechain.storage.fs} — one JSON file per block; requires no
 *       native libraries (FR-STOR-05).</li>
 * </ul>
 *
 * <p>All implementations share a single {@link com.privatechain.storage.BlockSerializer}
 * instance that holds a pre-configured Jackson {@code ObjectMapper} with
 * {@link com.fasterxml.jackson.datatype.jsr310.JavaTimeModule}, disabled
 * {@code FAIL_ON_UNKNOWN_PROPERTIES}, and field-level visibility. Every block is
 * hash-verified on load (NFR-SEC-03).</p>
 *
 * <p>{@link com.privatechain.storage.ChainExporter} provides three static utility
 * methods for chain export and import (FR-SER-02, FR-SER-03):
 * {@code toJson()}, {@code fromJson()}, and {@code toCsv()}.</p>
 *
 * @since 1.0.0
 */
package com.privatechain.storage;
