/**
 * Round-Robin consensus engine — deterministic slot-based block production for
 * development and testing (FR-CONS-05).
 *
 * <p>{@link com.privatechain.consensus.roundrobin.RoundRobinEngine} assigns each block
 * to the peer whose index equals {@code block.getIndex() % peers.size()}. Block
 * production is instant (no hash search) and fully deterministic given the same peer
 * list, making it ideal for repeatable integration tests and local demos.</p>
 *
 * <p>This engine provides no Byzantine fault tolerance and should not be used in
 * production deployments where block-producer identity must be cryptographically
 * verified.</p>
 *
 * @since 1.0.0
 */
package com.privatechain.consensus.roundrobin;
