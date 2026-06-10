/**
 * Built-in {@link com.privatechain.core.spi.ConsensusEngine} implementations for the
 * private-blockchain library.
 *
 * <p>Four production-ready engines are provided, covering the most common use cases
 * for permission blockchain networks:</p>
 * <ul>
 *   <li>{@link com.privatechain.consensus.pow} — SHA-256 Proof of Work with
 *       configurable difficulty and automatic recalibration.</li>
 *   <li>{@link com.privatechain.consensus.poa} — Proof of Authority; only nodes whose
 *       addresses are in a configured signer set may produce blocks (AC-02).</li>
 *   <li>{@link com.privatechain.consensus.pbft} — Practical Byzantine Fault Tolerance;
 *       two-phase (prepare → commit) protocol tolerating up to {@code f} Byzantine
 *       nodes where total validators ≥ 3f+1 (NFR-REL-03).</li>
 *   <li>{@link com.privatechain.consensus.roundrobin} — deterministic slot-based
 *       block production for local development and testing (FR-CONS-05).</li>
 * </ul>
 *
 * <p>{@link com.privatechain.consensus.ConsensusSupport} provides shared block-assembly
 * utilities used by all four engines and by custom engine implementations.</p>
 *
 * <p>Any class that implements {@link com.privatechain.core.spi.ConsensusEngine} can be
 * injected via {@link com.privatechain.core.builder.BlockchainConfig#builder()}
 * (FR-CONS-06). The built-in engines serve as reference implementations.</p>
 *
 * @since 1.0.0
 */
package com.privatechain.consensus;
