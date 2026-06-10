/**
 * Immutable domain objects — the fundamental data model of the private-blockchain library.
 *
 * <p>Three classes form the immutable core:</p>
 * <ul>
 *   <li>{@link com.privatechain.core.model.Block} — an immutable unit containing a list of
 *       transactions and a cryptographic link to the previous block (FR-CORE-01).</li>
 *   <li>{@link com.privatechain.core.model.BlockHeader} — a lightweight record holding the
 *       block's version, nonce, Merkle root, and timestamp for header-only validation
 *       (FR-CORE-05).</li>
 *   <li>{@link com.privatechain.core.model.Transaction} — the abstract base class that
 *       consumers must extend to define their own transaction types (FR-CORE-02).</li>
 * </ul>
 *
 * <p>All classes in this package are thread-safe and immutable after construction.</p>
 *
 * @since 1.0.0
 */
package com.privatechain.core.model;
