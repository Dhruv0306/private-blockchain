/**
 * Proof of Work (PoW) consensus engine — SHA-256 hash mining with configurable
 * difficulty (FR-CONS-02, AC-03).
 *
 * <p>{@link com.privatechain.consensus.pow.ProofOfWorkEngine} validates that a
 * block's hash starts with the required number of leading zero bits (the
 * {@code difficulty} parameter). Mining increments the block's {@code nonce} until
 * a valid hash is found. Default difficulty is 4 leading zero bits, which produces
 * hashes beginning with {@code "0000"} in hex.</p>
 *
 * <p>Difficulty is expressed as a leading-zero-bit count rather than a target integer,
 * which makes it platform-independent and easy to reason about for small networks.</p>
 *
 * @since 1.0.0
 */
package com.privatechain.consensus.pow;
