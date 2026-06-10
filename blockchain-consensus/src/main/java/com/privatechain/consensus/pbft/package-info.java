/**
 * Practical Byzantine Fault Tolerance (PBFT) consensus engine (FR-CONS-04, NFR-REL-03).
 *
 * <p>{@link com.privatechain.consensus.pbft.PBFTEngine} implements a two-phase
 * (prepare → commit) Byzantine fault-tolerant protocol. A block is accepted when at
 * least {@code 2f+1} validators have sent a commit message, where {@code f} is the
 * maximum number of Byzantine (arbitrarily faulty) nodes the network can tolerate.
 * The total validator count must satisfy {@code n ≥ 3f+1}.</p>
 *
 * <p>This implementation operates in-process. Distributed PBFT over the network layer
 * is deferred to a post-1.0 milestone (see backlog T-B06 and ADR-001).</p>
 *
 * @since 1.0.0
 */
package com.privatechain.consensus.pbft;
