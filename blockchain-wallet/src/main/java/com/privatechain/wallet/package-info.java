/**
 * Wallet and key management — secp256k1 key pairs, transaction signing, balance
 * computation, and encrypted keystore serialisation (FR-WALLET-01–04).
 *
 * <p>Classes in this package:</p>
 * <ul>
 *   <li>{@link com.privatechain.wallet.Wallet} — holds an
 *       {@link com.privatechain.crypto.ECKeyPair}, derives a Bitcoin-style
 *       Base58Check address, signs {@link com.privatechain.core.model.Transaction}
 *       instances via ECDSA secp256k1, and computes a balance by scanning all
 *       confirmed transactions in a given
 *       {@link com.privatechain.core.builder.Blockchain} (FR-WALLET-01,
 *       FR-WALLET-04).</li>
 *   <li>{@link com.privatechain.wallet.WalletManager} — creates wallets from fresh
 *       key pairs, imports wallets from a private-key hex string, and persists
 *       wallets to AES-256-GCM encrypted keystore JSON files with PBKDF2 key
 *       derivation (FR-WALLET-02, FR-WALLET-03).</li>
 * </ul>
 *
 * <p><strong>Security:</strong> Private key material is never written to logs or
 * exposed via {@code toString()} in any class in this package (NFR-SEC-01).
 * All random number generation uses {@link java.security.SecureRandom}
 * (NFR-SEC-05).</p>
 *
 * @since 1.0.0
 */
package com.privatechain.wallet;
