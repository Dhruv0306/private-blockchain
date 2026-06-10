/**
 * Unchecked exception hierarchy rooted at {@link com.privatechain.core.exception.BlockchainException}.
 *
 * <p>All exceptions thrown by this library extend {@code BlockchainException}, which is
 * itself an unchecked ({@code RuntimeException}) subclass. Consumers do not need to
 * declare checked exceptions in their code (NFR-UX-03).</p>
 *
 * <p>Concrete exception types:</p>
 * <ul>
 *   <li>{@link com.privatechain.core.exception.BlockValidationException} — thrown when a
 *       block fails any validation step in {@code Blockchain.addBlock()}.</li>
 *   <li>{@link com.privatechain.core.exception.ConsensusException} — thrown on an
 *       unrecoverable consensus-protocol error (e.g., empty peer list in RoundRobin).</li>
 *   <li>{@link com.privatechain.core.exception.TransactionValidationException} — thrown when
 *       a transaction is rejected by any {@code TransactionValidator} in the chain.</li>
 * </ul>
 *
 * @since 1.0.0
 */
package com.privatechain.core.exception;
