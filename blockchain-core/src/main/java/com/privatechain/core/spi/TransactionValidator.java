package com.privatechain.core.spi;

import com.privatechain.core.model.Transaction;

/**
 * Service Provider Interface (SPI) for pluggable transaction validation logic.
 *
 * <p>A {@code TransactionValidator} is called before a transaction enters the
 * {@link com.privatechain.core.builder.Blockchain} mempool and again before it is
 * included in a mined block. Multiple validators are composable in a
 * chain-of-responsibility pattern via the built-in {@code CompositeValidator}
 * (FR-TX-03).</p>
 *
 * <h2>Built-in implementations (in {@code blockchain-core})</h2>
 * <ul>
 *   <li>{@code SignatureTransactionValidator} — verifies ECDSA signature (FR-TX-04)</li>
 *   <li>{@code BalanceValidator} — checks sender has sufficient funds</li>
 *   <li>{@code CompositeValidator} — chains multiple validators</li>
 * </ul>
 *
 * <h2>Writing a custom validator</h2>
 * <pre>{@code
 * public class KycValidator implements TransactionValidator {
 *     \@Override
 *     public ValidationResult validate(Transaction tx, Blockchain chain) {
 *         if (!kycService.isApproved(tx.getSenderAddress())) {
 *             return ValidationResult.failure(
 *                 ValidationStatus.CUSTOM_REJECTION,
 *                 "Sender has not completed KYC verification");
 *         }
 *         return ValidationResult.success();
 *     }
 * }
 * }</pre>
 *
 * @see ValidationResult
 * @see com.privatechain.core.builder.BlockchainConfig
 * @since 1.0.0
 */
public interface TransactionValidator {

    /**
     * Validates a single transaction against the current chain state.
     *
     * <p>Implementations should be stateless and idempotent: calling this method
     * twice with the same arguments must return the same result. Side effects
     * (e.g., network calls) are permitted but must not mutate the chain.</p>
     *
     * <p>The method receives the full {@link com.privatechain.core.builder.Blockchain}
     * snapshot so that balance-checking validators can scan confirmed transactions
     * without additional infrastructure.</p>
     *
     * @param tx    the transaction to validate (non-null)
     * @param chain the current chain state used for balance and duplicate checks (non-null)
     * @return a {@link ValidationResult} indicating success or the first encountered failure
     */
    ValidationResult validate(Transaction tx, com.privatechain.core.builder.Blockchain chain);
}
