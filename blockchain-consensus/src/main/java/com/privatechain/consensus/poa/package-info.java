/**
 * Proof of Authority (PoA) consensus engine — accepts blocks only from a configured
 * set of authorized signer addresses (FR-CONS-03, AC-02).
 *
 * <p>{@link com.privatechain.consensus.poa.ProofOfAuthorityEngine} validates that
 * {@code block.getMinerAddress()} is present in the allowlisted signer set supplied
 * at construction time. This makes it suitable for permission networks where the
 * block-producing identity of each participant is known and trusted.</p>
 *
 * <p>PoA has no cryptographic mining cost and produces blocks instantly, making it
 * the preferred engine for production private-chain deployments where throughput
 * matters more than open participation.</p>
 *
 * @since 1.0.0
 */
package com.privatechain.consensus.poa;
