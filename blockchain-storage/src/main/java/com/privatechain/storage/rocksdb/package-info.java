/**
 * RocksDB persistent storage backend — high-throughput block persistence via the
 * {@code rocksdbjni} native library (FR-STOR-04).
 *
 * <p>{@link com.privatechain.storage.rocksdb.RocksDBStorage} uses the same key and
 * serialization scheme as {@link com.privatechain.storage.leveldb.LevelDBStorage}
 * (big-endian 4-byte block-index key, JSON value). RocksDB's LSM-tree architecture
 * provides higher write throughput than LevelDB under sustained load, making it the
 * preferred backend for high-frequency block production scenarios.</p>
 *
 * <p>Like LevelDB, RocksDB's write-ahead log prevents data corruption on abrupt JVM
 * termination (NFR-REL-01).</p>
 *
 * @since 1.0.0
 */
package com.privatechain.storage.rocksdb;
