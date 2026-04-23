# Requirements Specification
## private-blockchain — Java Maven Library

**Version:** 1.0.0-DRAFT  
**Status:** In Review

---

## 1. Introduction

### 1.1 Purpose
This document specifies the complete functional and non-functional requirements for the `private-blockchain` Java Maven library — a modular, embeddable private blockchain engine for Java applications.

### 1.2 Scope
The library covers: immutable block/transaction models, pluggable consensus engines, cryptographic primitives, a transaction mempool, P2P networking, configurable persistence, wallet/key management, and private network access control. It does not include a blockchain explorer UI, token economics, or a public network protocol.

### 1.3 Definitions

| Term    | Meaning                                                                                       |
|---------|-----------------------------------------------------------------------------------------------|
| Block   | Immutable unit of data containing transactions and a cryptographic link to the previous block |
| SPI     | Service Provider Interface — intended for consumer implementation                             |
| Mempool | In-memory pool of submitted, unconfirmed transactions                                         |
| PoW     | Proof of Work                                                                                 |
| PoA     | Proof of Authority                                                                            |
| PBFT    | Practical Byzantine Fault Tolerance                                                           |
| RBAC    | Role-Based Access Control                                                                     |

---

## 2. Product Description

### 2.1 Module Dependency Model
```
Application code
      │
      ▼
BlockchainNode  ← blockchain-core  (REQUIRED)
      │
      ├── ConsensusEngine  ← blockchain-consensus  (or user-provided)
      ├── BlockchainStorage  ← blockchain-storage  (or user-provided)
      ├── TransactionValidator  ← user-provided
      └── BlockchainEventListener  ← user-provided
```

### 2.2 Constraints
- Java 17+ (LTS)
- Maven 3.8+
- `blockchain-core` has zero mandatory transitive dependencies beyond the JDK
- All cryptographic operations use `java.security` as base (BouncyCastle allowed)
- Apache License 2.0

---

## 3. Functional Requirements

### 3.1 Core Data Model

**FR-CORE-01:** The library MUST provide an immutable `Block` class: `index` (long), `timestamp` (Instant), `previousHash` (String), `hash` (String), `nonce` (long), `merkleRoot` (String), `version` (int), `transactions` (immutable List).

**FR-CORE-02:** The library MUST provide an abstract `Transaction` base class users can extend. Base fields: `id` (UUID), `senderAddress`, `receiverAddress`, `amount` (BigDecimal), `timestamp` (Instant), `signature`, `metadata` (Map<String,Object>).

**FR-CORE-03:** All model classes MUST be immutable after construction. Builders MUST be provided.

**FR-CORE-04:** `Blockchain` MUST manage the ordered chain, enforce hash linkage, and delegate validation to the configured `ConsensusEngine`.

**FR-CORE-05:** `BlockHeader` MUST allow lightweight chain validation without loading transaction lists.

**FR-CORE-06:** `ChainValidator` MUST verify full chain integrity (hash chain, Merkle roots, signatures) without network access.

**FR-CORE-07:** A genesis block MUST be auto-created on new chain init. Its `previousHash` MUST be 32 zero bytes (hex).

### 3.2 Consensus Engine SPI

**FR-CONS-01:** `ConsensusEngine` interface MUST define:
- `boolean validateBlock(Block, Blockchain)`
- `Block mineBlock(List<Transaction>, Block previous)`
- `String engineName()`

**FR-CONS-02:** `ProofOfWorkEngine` — SHA-256 based, configurable difficulty (default: 4 leading zero bits).

**FR-CONS-03:** `ProofOfAuthorityEngine` — accepts blocks only from a configured set of authorized node addresses.

**FR-CONS-04:** `PBFTEngine` — 3-phase protocol (pre-prepare, prepare, commit) with 2f+1 quorum.

**FR-CONS-05:** `RoundRobinEngine` — slot-based ordered production for dev/testing.

**FR-CONS-06:** Any user class implementing `ConsensusEngine` MUST be injectable at config time.

**FR-CONS-07:** `DifficultyAdjuster` MUST auto-recalibrate PoW difficulty based on average block time over a configurable window.

### 3.3 Transaction Validator SPI

**FR-TX-01:** `TransactionValidator` MUST define: `ValidationResult validate(Transaction tx, Blockchain chain)`

**FR-TX-02:** `ValidationResult` MUST carry: `boolean success`, `List<String> errors`, `ValidationStatus` enum (`VALID`, `INVALID_SIGNATURE`, `INSUFFICIENT_FUNDS`, `DUPLICATE`, `CUSTOM_REJECTION`).

**FR-TX-03:** Multiple validators MUST be composable in a chain-of-responsibility pattern.

**FR-TX-04:** `SignatureTransactionValidator` MUST ship as a built-in implementation.

**FR-TX-05:** Custom `Transaction` subtypes MUST work with custom validators without modifying library source.

### 3.4 Storage SPI

**FR-STOR-01:** `BlockchainStorage` interface MUST define: `saveBlock(Block)`, `loadBlock(int)`, `loadBlockByHash(String)`, `loadAll()`, `exists(String)`, `chainHeight()`, `deleteAll()`.

**FR-STOR-02:** `InMemoryStorage` — HashMap-backed, for testing.

**FR-STOR-03:** `LevelDBStorage` — persistent, via `leveldbjni`.

**FR-STOR-04:** `RocksDBStorage` — persistent, high write throughput.

**FR-STOR-05:** `FileSystemStorage` — one JSON file per block, zero native deps.

**FR-STOR-06:** All storage implementations MUST be thread-safe for concurrent reads; writes MUST be serialized.

### 3.5 Cryptography

**FR-CRYPTO-01 — 03:** `HashUtil` MUST provide `sha256()`, `sha3_256()`, `doubleSha256()` returning hex strings.

**FR-CRYPTO-04:** `SignatureUtil.sign(byte[], PrivateKey)` MUST use ECDSA secp256k1.

**FR-CRYPTO-05:** `SignatureUtil.verify(byte[], Signature, PublicKey)` MUST return boolean.

**FR-CRYPTO-06:** `MerkleTree` MUST expose `buildRoot(List<String>)` and `getProof(String txId)`.

**FR-CRYPTO-07:** `MerkleProof` MUST contain sufficient data for third-party inclusion verification.

**FR-CRYPTO-08:** `KeyPairGenerator.generateECKeyPair()` MUST produce an `ECKeyPair` record.

**FR-CRYPTO-09:** `AddressUtil.deriveAddress(PublicKey)` MUST use SHA-256 followed by RIPEMD-160.

### 3.6 Mempool

**FR-MEMPOOL-01:** `TransactionMempool` MUST validate on submit, reject duplicates, and manage the pool lifecycle.

**FR-MEMPOOL-02:** `getTopN(int n)` MUST return transactions ordered by the configured `TransactionPrioritizer`.

**FR-MEMPOOL-03:** Built-in prioritizers: `FeeBasedPrioritizer` and `TimestampBasedPrioritizer`.

**FR-MEMPOOL-04:** `evictExpired(Duration ttl)` MUST remove stale transactions.

**FR-MEMPOOL-05:** Confirmed transactions MUST be auto-removed from the pool when a block is added.

### 3.7 Wallet

**FR-WALLET-01:** `Wallet` MUST hold `ECKeyPair`, expose `getAddress()`, and provide `sign(Transaction)`.

**FR-WALLET-02:** `WalletManager` MUST support: `createWallet()`, `importWallet(String pkHex)`, `exportKeystore(Wallet, String password)`, `importKeystore(String json, String password)`.

**FR-WALLET-03:** Keystore files MUST be encrypted AES-128-CTR with PBKDF2 MAC (Web3 Secret Storage v3).

**FR-WALLET-04:** `Wallet.getBalance(Blockchain)` MUST compute balance by scanning confirmed transactions.

### 3.8 Networking / P2P

**FR-NET-01:** `PeerManager` MUST maintain the peer list, check connectivity, and prune unresponsive peers.

**FR-NET-02:** `BlockBroadcaster` MUST propagate newly mined blocks to all connected peers.

**FR-NET-03:** `GossipProtocol` MUST forward blocks/txs to k random peers (configurable fan-out k).

**FR-NET-04:** `SyncManager` MUST fetch and apply missing blocks on startup and after partition recovery.

**FR-NET-05:** `ForkResolver` MUST select the chain with the greatest cumulative difficulty on fork.

**FR-NET-06:** All P2P messages MUST use Protocol Buffers (proto3) framing.

**FR-NET-07:** `NodeServer` MUST support a configurable max peer connection count (default: 25).

### 3.9 Access Control

**FR-AC-01:** `AllowlistManager` MUST reject blocks/transactions from non-allowlisted nodes.

**FR-AC-02:** `PermissionManager` MUST implement RBAC with roles: `ADMIN`, `MINER`, `VALIDATOR`, `OBSERVER`.

**FR-AC-03 — Role capabilities:**

| Role      | Can Submit Block | Can Submit Tx | Can Validate | Can Read |
|-----------|------------------|---------------|--------------|----------|
| ADMIN     | Yes              | Yes           | Yes          | Yes      |
| MINER     | Yes              | Yes           | No           | Yes      |
| VALIDATOR | No               | No            | Yes          | Yes      |
| OBSERVER  | No               | No            | No           | Yes      |

**FR-AC-04:** `InvitationService` MUST allow ADMIN to sign a time-limited `InvitationToken` for a new node.

**FR-AC-05:** `InvitationToken` MUST be rejected after its expiry timestamp even if the signature is valid.

### 3.10 Events

**FR-EVENT-01:** `BlockchainEventBus` MUST publish: block added, transaction submitted, transaction confirmed, fork detected, peer connected, peer disconnected.

**FR-EVENT-02:** Multiple `BlockchainEventListener` implementations MUST be registerable without source modification.

**FR-EVENT-03:** Event delivery MUST be asynchronous and MUST NOT delay the triggering operation.

### 3.11 Configuration

**FR-CFG-01:** `BlockchainConfig.builder()` MUST be the single assembly point:
```java
BlockchainNode node = BlockchainConfig.builder()
    .consensusEngine(new ProofOfAuthorityEngine(authorizedNodes))
    .transactionValidator(new MyValidator())
    .storage(new LevelDBStorage("/data/chain"))
    .networkPort(8545)
    .blockTimeSeconds(5)
    .build();
node.start();
```

**FR-CFG-02:** `BlockchainConfig.builder().build()` with no other calls MUST produce a working in-memory PoW chain.

**FR-CFG-03:** `blockchain-spring` MUST provide `BlockchainAutoConfiguration` reading `privatechain.*` from `application.yml`.

### 3.12 Serialization

**FR-SER-01:** All models MUST support Jackson JSON with polymorphic `Transaction` type resolution via `@JsonTypeInfo`.

**FR-SER-02:** `ChainExporter.toJson(Blockchain)` and `ChainExporter.fromJson(String)` MUST be provided.

**FR-SER-03:** `ChainExporter.toCsv(Blockchain)` MUST be provided for transaction-level audit exports.

---

## 4. Non-Functional Requirements

### 4.1 Performance

| ID          | Metric                                      | Target                            |
|-------------|---------------------------------------------|-----------------------------------|
| NFR-PERF-01 | Block validation (up to 1,000 txs)          | < 100 ms                          |
| NFR-PERF-02 | `Mempool.submit()` including sig validation | < 5 ms                            |
| NFR-PERF-03 | `LevelDBStorage.saveBlock()` (1,000 txs)    | < 20 ms                           |
| NFR-PERF-04 | `HashUtil.sha256()` throughput              | ≥ 50,000 hashes/sec single thread |
| NFR-PERF-05 | Gossip broadcast to 25 peers (LAN)          | < 500 ms                          |

### 4.2 Reliability
- `NFR-REL-01`: LevelDB/RocksDB MUST be crash-safe; abrupt JVM kill MUST NOT corrupt storage.
- `NFR-REL-02`: `SyncManager` MUST auto-restore a node to canonical chain after single-node failure.
- `NFR-REL-03`: `PBFTEngine` MUST tolerate up to f Byzantine nodes where total validators ≥ 3f+1.

### 4.3 Security
- `NFR-SEC-01`: Private keys MUST NEVER appear in logs, `toString()`, or unencrypted serialization.
- `NFR-SEC-02`: Signature verification MUST occur before any transaction enters the mempool.
- `NFR-SEC-03`: Block hashes MUST be recomputed on chain load; mismatch MUST throw `ChainCorruptException`.
- `NFR-SEC-04`: Allowlist check MUST occur before deserialization of network messages.
- `NFR-SEC-05`: All RNG MUST use `java.security.SecureRandom`.
- `NFR-SEC-06`: CI MUST fail on any OWASP Dependency-Check finding with CVSS ≥ 7.0.

### 4.4 Developer Experience
- `NFR-UX-01`: Every public API method MUST have full Javadoc with `@param`, `@return`, `@throws`.
- `NFR-UX-02`: A working first example MUST require ≤ 10 lines of code.
- `NFR-UX-03`: All public exceptions MUST extend `BlockchainException` (unchecked).
- `NFR-UX-04`: `BlockchainNode.status()` MUST return chain height, mempool size, peer count, last block time.

### 4.5 Compatibility
- Java 17, 21, and 23 MUST be supported.
- `blockchain-core` MUST have zero mandatory transitive dependencies.
- All modules MUST share the same semantic version number per release.

### 4.6 Quality
- Unit test coverage ≥ 80% (JaCoCo) for `blockchain-core` and `blockchain-crypto`.
- Every `ConsensusEngine` implementation MUST have an integration test with ≥ 3 nodes.
- Every SPI implementation MUST pass a Technology Compatibility Kit (TCK) test.
- All code MUST pass Checkstyle with Google Java Style.

---

## 5. Acceptance Criteria

| ID    | Area      | Criterion                                                                     |
|-------|-----------|-------------------------------------------------------------------------------|
| AC-01 | Core      | Same inputs produce the same block hash across JVM restarts                   |
| AC-02 | PoA       | 3-node cluster accepts only authorized-signer blocks                          |
| AC-03 | PoW       | Difficulty=4 produces hashes beginning with "0000"                            |
| AC-04 | Storage   | LevelDB chain survives JVM kill and reloads identically                       |
| AC-05 | Crypto    | Tampered transaction signature is rejected by `SignatureTransactionValidator` |
| AC-06 | Network   | Joining node syncs to correct chain height within 5 seconds                   |
| AC-07 | Access    | Non-allowlisted peer block is silently dropped and logged                     |
| AC-08 | SPI       | Custom `ConsensusEngine` is called for every `addBlock()`                     |
| AC-09 | Extension | Custom `Transaction` subclass survives full JSON round-trip                   |
| AC-10 | Spring    | `blockchain-spring` auto-creates `BlockchainNode` bean with zero config       |
