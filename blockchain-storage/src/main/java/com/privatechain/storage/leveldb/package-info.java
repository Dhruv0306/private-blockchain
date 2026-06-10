/**
 * LevelDB persistent storage backend — crash-safe block persistence via the
 * {@code leveldbjni} native library (FR-STOR-03, NFR-REL-01).
 *
 * <p>{@link com.privatechain.storage.leveldb.LevelDBStorage} maps block index
 * {@code n} to a big-endian 4-byte key and stores the full block as a JSON value
 * produced by {@link com.privatechain.storage.BlockSerializer}. On load, each
 * block's hash is recomputed and compared against the stored hash; a mismatch
 * causes an immediate {@link com.privatechain.core.exception.BlockValidationException}
 * (NFR-SEC-03).</p>
 *
 * <p>LevelDB's write-ahead log guarantees that an abrupt JVM kill cannot corrupt
 * the stored chain (NFR-REL-01).</p>
 *
 * @since 1.0.0
 */
package com.privatechain.storage.leveldb;
