/**
 * File-system storage backend — persists each block as an individual JSON file
 * in a configurable directory (FR-STOR-05).
 *
 * <p>{@link com.privatechain.storage.fs.FileSystemStorage} writes block index
 * {@code n} to {@code <dir>/block-<n>.json} using
 * {@link com.privatechain.storage.BlockSerializer}. It requires no native libraries
 * and is therefore portable across all JDK 17+ platforms.</p>
 *
 * <p>All file path inputs are sanitized before use; SpotBugs
 * {@code PATH_TRAVERSAL_IN} findings are suppressed with documented justification
 * where the sanitization cannot be expressed in a form the interprocedural analyzer
 * can verify statically.</p>
 *
 * <p>Thread safety: writes are serialized via a {@code ReentrantLock}; reads use a
 * shared {@code ReadLock} (FR-STOR-06).</p>
 *
 * @since 1.0.0
 */
package com.privatechain.storage.fs;
