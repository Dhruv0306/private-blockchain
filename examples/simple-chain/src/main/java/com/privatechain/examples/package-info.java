/**
 * Simple-chain example — demonstrates the core library end-to-end in a single
 * runnable {@code main} method (T-074, NFR-UX-02).
 *
 * <p>Classes in this package:</p>
 * <ul>
 *   <li>{@link com.privatechain.examples.MoneyTransferTransaction} — a concrete
 *       {@link com.privatechain.core.model.Transaction} subtype that adds an
 *       ISO-4217 {@code currency} field. The currency is included in
 *       {@code toSignableBytes()} so it is cryptographically bound to the ECDSA
 *       signature. Demonstrates FR-TX-05 and AC-09 (full JSON round-trip via
 *       Jackson {@code @JsonTypeInfo}).</li>
 *   <li>{@link com.privatechain.examples.SimpleChainDemo} — a ten-step runnable
 *       demo: node creation (≤10 lines — NFR-UX-02), wallet generation,
 *       transaction signing and submission, PoW block mining, chain validation,
 *       JSON export and round-trip, CSV export, and balance query.</li>
 * </ul>
 *
 * <p>Run with:</p>
 * <pre>{@code
 * mvn exec:java -pl examples/simple-chain
 * }</pre>
 *
 * @since 1.0.0
 */
package com.privatechain.examples;
