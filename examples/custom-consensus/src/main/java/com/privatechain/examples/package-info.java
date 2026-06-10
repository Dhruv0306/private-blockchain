/**
 * Custom consensus example — demonstrates implementing the
 * {@link com.privatechain.core.spi.ConsensusEngine} SPI and injecting it via
 * {@link com.privatechain.core.builder.BlockchainConfig#builder()} (T-074,
 * FR-CONS-06, AC-08).
 *
 * <p>Classes in this package:</p>
 * <ul>
 *   <li>{@link com.privatechain.examples.VotingConsensusEngine} — a majority-vote
 *       engine where block acceptance requires explicit approval votes from a strict
 *       majority of registered validators ({@code votes &gt; floor(n/2)}). Votes are
 *       tracked in a {@link java.util.concurrent.ConcurrentHashMap} per block hash.
 *       In this demo, {@code mineBlock()} auto-casts votes from all validators;
 *       in a real distributed system, votes would arrive asynchronously over the
 *       network.</li>
 *   <li>{@link com.privatechain.examples.VotingConsensusDemo} — a three-part runnable
 *       demo: quorum success, quorum failure followed by recovery, and engine name
 *       visibility in {@link com.privatechain.core.builder.BlockchainNode#status()}.</li>
 * </ul>
 *
 * <p>Run with:</p>
 * <pre>{@code
 * mvn exec:java -pl examples/custom-consensus
 * }</pre>
 *
 * @since 1.0.0
 */
package com.privatechain.examples;
