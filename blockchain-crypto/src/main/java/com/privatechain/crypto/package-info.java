/**
 * Cryptographic primitives for the private-blockchain library — hashing, ECDSA
 * signing, key-pair generation, address derivation, and Merkle trees.
 *
 * <p>All classes in this package are stateless utilities (all methods are
 * {@code static}) backed by Bouncy Castle 1.84 over the {@code secp256k1} elliptic
 * curve. The module adds no mandatory transitive dependencies beyond Bouncy Castle —
 * consuming modules that do not need cryptography can depend solely on
 * {@code blockchain-core}.</p>
 *
 * <p>Key classes:</p>
 * <ul>
 *   <li>{@link com.privatechain.crypto.HashUtil} — {@code sha256()}, {@code sha3_256()},
 *       {@code doubleHash()}; all return lowercase hex strings (FR-CRYPTO-01–03).</li>
 *   <li>{@link com.privatechain.crypto.ECKeyPair} — immutable secp256k1 key pair;
 *       {@code toString()} masks the private key with {@code [REDACTED]} (NFR-SEC-01).</li>
 *   <li>{@link com.privatechain.crypto.KeyPairGenerator} — generates and imports
 *       {@link com.privatechain.crypto.ECKeyPair} instances (FR-CRYPTO-08).</li>
 *   <li>{@link com.privatechain.crypto.ECDSASignatureUtil} — ECDSA sign and verify
 *       over secp256k1 (FR-CRYPTO-04, FR-CRYPTO-05).</li>
 *   <li>{@link com.privatechain.crypto.AddressUtil} — derives a blockchain address
 *       from a public key via SHA-256 → RIPEMD-160 → Base58Check (FR-CRYPTO-09).</li>
 *   <li>{@link com.privatechain.crypto.MerkleTree} — builds Merkle roots and
 *       generates inclusion proofs for transaction sets (FR-CRYPTO-06, FR-CRYPTO-07).</li>
 * </ul>
 *
 * <p><strong>Security note:</strong> All random number generation uses
 * {@link java.security.SecureRandom} (NFR-SEC-05). Private key material is never
 * written to logs or returned from {@code toString()} in any class in this package.</p>
 *
 * @since 1.0.0
 */
package com.privatechain.crypto;
