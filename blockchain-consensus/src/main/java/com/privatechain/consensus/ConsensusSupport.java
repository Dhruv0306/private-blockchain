package com.privatechain.consensus;

import com.privatechain.core.model.Block;
import com.privatechain.core.model.BlockHeader;
import com.privatechain.core.model.Transaction;
import com.privatechain.crypto.MerkleTree;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Shared helper methods for built-in consensus engines.
 *
 * <p>The consensus module follows the same explicit-wiring philosophy as the rest
 * of the codebase: each engine is a concrete implementation of
 * {@link com.privatechain.core.spi.ConsensusEngine}, and common block-construction
 * logic lives in a single utility class so the individual engines stay focused on
 * their own policy.</p>
 *
 * <p>The helpers in this class are responsible for three recurring tasks:</p>
 * <ul>
 *   <li>validating configured peer / validator collections,</li>
 *   <li>building blocks in a canonical, deterministic way, and</li>
 *   <li>checking shared integrity constraints such as Merkle-root and prefix rules.</li>
 * </ul>
 *
 * <p>All methods are stateless and thread-safe.</p>
 *
 * @since 1.0.0
 */
public final class ConsensusSupport {

    /**
     * Prevents instantiation.
     */
    private ConsensusSupport() {
        throw new UnsupportedOperationException("ConsensusSupport is a utility class");
    }

    /**
     * Returns a validated, defensive copy of the supplied string values.
     *
     * <p>The returned list is immutable and can optionally be sorted into
     * deterministic lexicographic order. This is useful for consensus policies
     * that should produce stable output regardless of caller input ordering.</p>
     *
     * @param values the values to validate and copy
     * @param label  human-readable label used in exception messages
     * @param sort   whether to sort the copy into deterministic lexicographic order
     * @return an immutable list containing the validated values
     * @throws NullPointerException     if {@code values} or any element is null
     * @throws IllegalArgumentException if any element is blank
     */
    public static List<String> copyAndValidate(Collection<String> values, String label, boolean sort) {
        Objects.requireNonNull(values, label + " must not be null");

        List<String> copy = new ArrayList<>(values.size());
        for (String value : values) {
            Objects.requireNonNull(value, label + " must not contain null values");
            if (value.isBlank()) {
                throw new IllegalArgumentException(label + " must not contain blank values");
            }
            copy.add(value);
        }
        if (sort) {
            copy.sort(String::compareTo);
        }
        return List.copyOf(copy);
    }

    /**
     * Computes the canonical Merkle root for the supplied transactions.
     *
     * <p>The Merkle tree is built from transaction identifiers in the exact order
     * supplied by the caller. That keeps the block hash deterministic and makes the
     * resulting root suitable for inclusion in a block header.</p>
     *
     * @param transactions the transactions to commit into the block
     * @return the Merkle root derived from the ordered transaction IDs
     * @throws NullPointerException if {@code transactions} or any transaction is null
     */
    public static String merkleRoot(List<Transaction> transactions) {
        Objects.requireNonNull(transactions, "transactions must not be null");

        List<String> txIds = new ArrayList<>(transactions.size());
        for (Transaction transaction : transactions) {
            Objects.requireNonNull(transaction, "transactions must not contain null values");
            txIds.add(transaction.getId().toString());
        }
        return MerkleTree.buildRoot(txIds);
    }

    /**
     * Builds a canonical block from consensus-specific inputs.
     *
     * <p>The returned block uses the previous block hash, the supplied ordered
     * transactions, and a header populated with the provided metadata. Each engine
     * supplies the header values that make sense for its protocol (for example,
     * proof-of-work uses the difficulty field as a mining target marker, while
     * authority-based engines use the miner address).</p>
     *
     * @param previousBlock the chain tip the block extends
     * @param transactions  ordered transactions to include
     * @param bits          engine-specific compact target or protocol marker
     * @param nonce         nonce value to embed in the block header
     * @param minerAddress  address of the producing node, or {@code null}
     * @param timestamp     block creation time
     * @return a freshly constructed immutable block
     * @throws NullPointerException if {@code previousBlock}, {@code transactions}, or
     *                              {@code timestamp} is null
     */
    public static Block buildBlock(Block previousBlock,
                                   List<Transaction> transactions,
                                   int bits,
                                   long nonce,
                                   String minerAddress,
                                   Instant timestamp) {
        Objects.requireNonNull(previousBlock, "previousBlock must not be null");
        Objects.requireNonNull(transactions, "transactions must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");

        BlockHeader header = BlockHeader.builder()
            .bits(bits)
            .nonce(nonce)
            .merkleRoot(merkleRoot(transactions))
            .timestamp(timestamp)
            .build();

        return Block.builder()
            .index(previousBlock.getIndex() + 1)
            .previousHash(previousBlock.getHash())
            .transactions(transactions)
            .header(header)
            .minerAddress(minerAddress)
            .build();
    }

    /**
     * Returns {@code true} if the supplied block is the genesis block.
     *
     * @param block the block to inspect
     * @return {@code true} if the block has index 0
     * @throws NullPointerException if {@code block} is null
     */
    public static boolean isGenesis(Block block) {
        Objects.requireNonNull(block, "block must not be null");
        return block.getIndex() == 0;
    }

    /**
     * Verifies that a block hash satisfies a leading-zero-bit constraint.
     *
     * <p>The hash is supplied as lowercase hexadecimal, so the check is performed
     * at bit precision instead of assuming a nibble-aligned prefix. This keeps the
     * helper useful for both coarse and fine-grained difficulty settings.</p>
     *
     * @param hexHash         lowercase hex-encoded SHA-256 hash
     * @param leadingZeroBits required number of leading zero bits
     * @return {@code true} if the hash satisfies the requested prefix constraint
     * @throws NullPointerException if {@code hexHash} is null
     */
    public static boolean hasLeadingZeroBits(String hexHash, int leadingZeroBits) {
        Objects.requireNonNull(hexHash, "hexHash must not be null");
        if (leadingZeroBits <= 0) {
            return true;
        }

        byte[] bytes = java.util.HexFormat.of().parseHex(hexHash);
        int requiredBytes = (leadingZeroBits + 7) / 8;
        if (requiredBytes > bytes.length) {
            return false;
        }

        int fullZeroBytes = leadingZeroBits / 8;
        int remainingBits = leadingZeroBits % 8;

        for (int i = 0; i < fullZeroBytes; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }

        if (remainingBits == 0) {
            return true;
        }

        int mask = 0xFF << (8 - remainingBits);
        return (bytes[fullZeroBytes] & mask) == 0;
    }

    /**
     * Performs the baseline integrity checks shared by all consensus engines.
     *
     * <p>The check verifies that the stored block hash still matches the block
     * contents and that the Merkle root in the header matches the transactions
     * carried by the block.</p>
     *
     * @param block the candidate block
     * @return {@code true} if the block hash and Merkle root both match the payload
     * @throws NullPointerException if {@code block} is null
     */
    public static boolean hasConsistentIntegrity(Block block) {
        Objects.requireNonNull(block, "block must not be null");
        return block.isHashValid() && block.getMerkleRoot().equals(merkleRoot(block.getTransactions()));
    }
}

