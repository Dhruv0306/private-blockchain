package com.privatechain.wallet;

import com.privatechain.core.builder.Blockchain;
import com.privatechain.core.model.Block;
import com.privatechain.core.model.Transaction;
import com.privatechain.crypto.AddressUtil;
import com.privatechain.crypto.ECDSASignatureUtil;
import com.privatechain.crypto.ECKeyPair;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Immutable wallet holding an EC key pair, a derived blockchain address,
 * and performing transaction signing and balance queries.
 *
 * <p>A wallet is the primary interface for end-users to interact with the blockchain.
 * It holds a secp256k1 key pair, derives a unique blockchain address from the public key,
 * and provides methods to sign transactions and compute balances (FR-WALLET-01,
 * FR-WALLET-04).</p>
 *
 * <h2>Key management</h2>
 * <p>The wallet's private key is never exposed via public API. Instead, callers
 * invoke {@link #sign(Transaction)} to produce a signed transaction. The private key
 * is accessible only to trusted code during import/export operations.</p>
 *
 * <h2>Address derivation</h2>
 * <p>The blockchain address is derived once during construction via
 * {@link AddressUtil#deriveAddress(java.security.PublicKey)} and is immutable.</p>
 *
 * <h2>Thread safety</h2>
 * <p>Wallet is immutable; all methods are thread-safe.</p>
 *
 * @see AddressUtil
 * @see ECDSASignatureUtil
 * @since 1.0.0
 */
public final class Wallet {

    private static final Logger LOGGER = Logger.getLogger(Wallet.class.getName());

    // ─── Fields ───────────────────────────────────────────────────────────────

    private final ECKeyPair keyPair;
    private final String address;

    // ─── Constructor ──────────────────────────────────────────────────────────

    /**
     * Constructs a wallet from an {@link ECKeyPair}.
     *
     * <p>The blockchain address is derived immediately and stored immutably.
     * Package-private to enforce creation via WalletManager.</p>
     *
     * @param keyPair the EC key pair (non-null)
     * @throws NullPointerException if keyPair is null
     */
    Wallet(ECKeyPair keyPair) {
        this.keyPair = Objects.requireNonNull(keyPair, "keyPair must not be null");
        this.address = AddressUtil.deriveAddress(keyPair.getPublicKey());
    }

    // ─── Public accessors ──────────────────────────────────────────────────────

    /**
     * Returns the blockchain address derived from this wallet's public key.
     *
     * @return non-null, Base58Check-encoded address
     */
    public String getAddress() {
        return address;
    }

    /**
     * Returns the underlying EC key pair (package-private for trusted access).
     *
     * @return non-null {@link ECKeyPair}
     */
    ECKeyPair getKeyPair() {
        return keyPair;
    }

    // ─── Transaction signing ──────────────────────────────────────────────────

    /**
     * Signs a transaction with this wallet's private key.
     *
     * <p>The transaction is modified in-place (signature bytes are attached).
     * The signer's address should match {@link #getAddress()}, but this is not
     * enforced here — that validation occurs in the consensus layer.</p>
     *
     * @param transaction the transaction to sign (non-null)
     * @return the same transaction, now with a signature attached
     * @throws NullPointerException  if transaction is null
     * @throws IllegalStateException if signing fails (e.g., invalid key)
     */
    public Transaction sign(Transaction transaction) {
        Objects.requireNonNull(transaction, "transaction must not be null");

        try {
            byte[] signableBytes = transaction.toSignableBytes();
            byte[] signature = ECDSASignatureUtil.sign(signableBytes, keyPair);
            transaction.sign(signature);

            LOGGER.fine(() -> "Transaction signed: " + transaction.getId());
            return transaction;
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to sign transaction: " + e.getMessage(), e);
        }
    }

    // ─── Balance queries ──────────────────────────────────────────────────────

    /**
     * Computes the balance of this wallet by scanning the blockchain for all
     * confirmed transactions involving this address.
     *
     * <p>The balance is the sum of all incoming transactions minus the sum
     * of all outgoing transactions. Only transactions that have been included
     * in a block are counted (FR-WALLET-04).</p>
     *
     * @param blockchain the blockchain to query (non-null)
     * @return the wallet's balance as a non-negative {@link BigDecimal}
     * @throws NullPointerException if blockchain is null
     */
    public BigDecimal getBalance(Blockchain blockchain) {
        Objects.requireNonNull(blockchain, "blockchain must not be null");

        BigDecimal balance = BigDecimal.ZERO;

        // Iterate through all blocks
        for (Block block : blockchain.getChain()) {
            for (Transaction tx : block.getTransactions()) {
                // Add incoming transactions
                if (address.equals(tx.getReceiverAddress())) {
                    balance = balance.add(tx.getAmount());
                }
                // Subtract outgoing transactions
                if (address.equals(tx.getSenderAddress())) {
                    balance = balance.subtract(tx.getAmount());
                }
            }
        }

        return balance;
    }

    // ─── Object overrides ─────────────────────────────────────────────────────

    /**
     * Returns a human-readable summary of the wallet.
     *
     * <p>The private key is never included in the output (NFR-SEC-01).</p>
     *
     * @return string representation
     */
    @Override
    public String toString() {
        return "Wallet{"
            + "address=" + address
            + ", keyPair=" + keyPair
            + '}';
    }

    /**
     * Two wallets are equal if they derive the same address.
     *
     * @param obj object to compare
     * @return {@code true} if obj is a Wallet with the same address
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Wallet other)) {
            return false;
        }
        return Objects.equals(address, other.address);
    }

    /**
     * Hash code based on the wallet's address.
     *
     * @return hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(address);
    }
}



