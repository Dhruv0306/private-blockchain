package com.privatechain.core.builder;

import com.privatechain.core.model.Block;
import com.privatechain.core.model.BlockHeader;

import java.time.Instant;
import java.util.Collections;

/**
 * Factory for creating the genesis block (index 0) of a new blockchain.
 *
 * <p>The genesis block is special in two ways (FR-CORE-07):</p>
 * <ol>
 *   <li>Its {@code previousHash} is the 64-character all-zero string, representing
 *       "no previous block".</li>
 *   <li>It contains no transactions; its Merkle root is the empty-tree sentinel.</li>
 * </ol>
 *
 * <p>The factory creates a <em>deterministic</em> genesis block: the same
 * {@code chainId} always produces the same block hash, which is important for
 * nodes in the same network to agree on the starting point.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * Block genesis = GenesisBlockFactory.create("my-private-chain");
 * }</pre>
 *
 * <p>The genesis block created here is accepted by all built-in
 * {@link com.privatechain.core.spi.ConsensusEngine} implementations without
 * requiring consensus (it is the initial state, not a mined block).</p>
 *
 * @since 1.0.0
 */
public final class GenesisBlockFactory {

    /**
     * Utility class — no instances.
     */
    private GenesisBlockFactory() {
        throw new UnsupportedOperationException("GenesisBlockFactory is a utility class");
    }

    /**
     * Creates a deterministic genesis block for the given chain identifier.
     *
     * <p>The chain ID is embedded in the block's Merkle root computation to ensure
     * that two chains with different IDs cannot share the same genesis block hash,
     * preventing accidental cross-chain block acceptance.</p>
     *
     * @param chainId a stable, non-blank identifier for this blockchain network
     *                (e.g., {@code "acme-supply-chain-v1"}); must not change across restarts
     * @return the fully constructed, hash-verified genesis block
     * @throws NullPointerException     if chainId is null
     * @throws IllegalArgumentException if chainId is blank
     */
    public static Block create(String chainId) {
        if (chainId == null) {
            throw new NullPointerException("chainId must not be null");
        }
        if (chainId.isBlank()) {
            throw new IllegalArgumentException("chainId must not be blank");
        }

        // Deterministic genesis Merkle root: SHA-256 hex of the chain ID
        // We compute a stable placeholder rather than delegating to blockchain-crypto
        // (blockchain-core must have zero external deps — FR-CORE-01 / design §7.1).
        String merkleRoot = simpleGenesisRoot(chainId);

        BlockHeader genesisHeader = BlockHeader.builder()
            .version(1)
            .bits(0x1d00ffff)   // standard difficulty target (same as Bitcoin genesis)
            .nonce(0L)
            .merkleRoot(merkleRoot)
            .timestamp(Instant.EPOCH) // deterministic: epoch = 1970-01-01T00:00:00Z
            .build();

        return Block.builder()
            .index(0)
            .previousHash(Block.GENESIS_PREVIOUS_HASH)
            .transactions(Collections.emptyList())
            .header(genesisHeader)
            .build();
    }

    /**
     * Creates a genesis block with the default chain ID {@code "private-blockchain"}.
     *
     * <p>Suitable for quick-start scenarios and tests that do not need a custom
     * network identifier.</p>
     *
     * @return the default genesis block
     */
    public static Block createDefault() {
        return create("private-blockchain");
    }

    /**
     * Computes a deterministic 64-character hex string from a chain ID using
     * the JDK's built-in SHA-256, keeping {@code blockchain-core} dependency-free.
     *
     * @param chainId the chain identifier to hash
     * @return 64-character lowercase hex string
     */
    private static String simpleGenesisRoot(String chainId) {
        try {
            java.security.MessageDigest digest =
                java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(
                chainId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hashBytes);
        } catch (java.security.NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JDK spec — unreachable in practice
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
