# Changelog

All notable changes to **private-blockchain** are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versioning follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0] — 2026-06-06

First stable release. All ten milestones (M0–M10) complete.

### Summary

A modular, embeddable Java 17+ library for building permission blockchain networks.
Plug in your own consensus logic, transaction types, and storage backend in ≤ 10 lines.
Zero mandatory dependencies in `blockchain-core`; Bouncy Castle, Netty, and LevelDB are
isolated to their respective modules.

---

### Added — Milestone 0: Project scaffold

- Multi-module Maven build (`pom.xml`) with 9 library modules and 3 example modules.
- `maven-compiler-plugin` targeting Java 17 with `--release` flag and `-Werror`.
- Checkstyle with Google Java Style (max line 120).
- SpotBugs with `findsecbugs-plugin`; fails on MEDIUM and higher findings.
- JaCoCo enforcing ≥ 80% line coverage on `blockchain-core` and `blockchain-crypto`.
- GitHub Actions: `build.yml` (JDK 17 + 21 matrix), `release.yml`, `qodana_code_quality.yml`.
- `.editorconfig`, `.gitignore`, `CONTRIBUTING.md`, `LICENSE` (Apache 2.0).

---

### Added — Milestone 1: Core data model

- **`Block`** — immutable class with `index`, `previousHash`, `hash`, `header`,
  `transactions` (unmodifiable List), `minerAddress`. Hash computed at construction via
  SHA-256 in `Block.computeHash()`.
- **`BlockHeader`** — record holding `version`, `bits`, `nonce`, `merkleRoot`, `timestamp`.
- **`Transaction`** — abstract base class with `id`, `senderAddress`, `receiverAddress`,
  `amount`, `timestamp`, `signature`, `metadata`. Annotated with Jackson
  `@JsonTypeInfo(use = Id.CLASS)` for zero-registration polymorphic round-trips (AC-09).
- **`Blockchain`** — chain manager with `addBlock()` (hash linkage + consensus validation),
  `isChainValid()` (full tamper detection without network), `getBlock()`, `getLatestBlock()`,
  `size()`. Thread-safe via `ReadWriteLock`.
- **`BlockchainConfig`** — fluent builder; `build()` with no args produces a working
  in-memory PoW node (FR-CFG-02).
- **`BlockchainNode`** — top-level entry point; `start()`, `stop()`, `status()`,
  `submitTransaction()`, `getChain()`, `getMempool()`, `getEventBus()`.
- **`GenesisBlockFactory`** — creates a deterministic genesis block with
  `previousHash = "0000...0000"` (64 zeros) (FR-CORE-07).
- SPI interfaces: `ConsensusEngine`, `TransactionValidator`, `BlockchainStorage`,
  `TransactionPrioritizer`, `ValidationResult`.
- Event system: sealed `BlockchainEvent` with 5 permitted inner types
  (`BlockAddedEvent`, `TransactionSubmittedEvent`, `PeerConnectedEvent`,
  `PeerDisconnectedEvent`, `ForkDetectedEvent`).
- `BlockchainEventBus` — `CopyOnWriteArrayList` backed; daemon executor; shutdown guard.
- Exception hierarchy: `BlockchainException` (abstract, unchecked) extended by
  `BlockValidationException`, `ConsensusException`, `TransactionValidationException`.
- `BlockSerializer` — shared `ObjectMapper` with `JavaTimeModule`, `FAIL_ON_UNKNOWN_PROPERTIES`
  disabled, getter visibility NONE, field visibility ANY. Used by all storage backends.
- `package-info.java` added to all 8 packages in `blockchain-core` (M10 — T-076).

---

### Added — Milestone 2: Cryptography

- **`HashUtil`** — `sha256(String/byte[])`, `sha3_256(String)`, `doubleHash(String)`;
  returns lowercase hex strings. Backed by Bouncy Castle 1.84.
- **`ECKeyPair`** — immutable record holding `PublicKey` + `PrivateKey` over secp256k1.
  `toString()` masks the private key with `[REDACTED]` (NFR-SEC-01).
- **`KeyPairGenerator`** — `generateECKeyPair()`, `fromPrivateKeyHex()`,
  `fromRawPrivateScalar()`.
- **`ECDSASignatureUtil`** — `sign(byte[], PrivateKey)`, `sign(byte[], ECKeyPair)`,
  `verify(byte[], byte[], PublicKey)`, `verify(byte[], byte[], ECKeyPair)`.
- **`AddressUtil`** — `deriveAddress(PublicKey)` using SHA-256 → RIPEMD-160 → Base58Check.
- **`MerkleTree`** — `buildRoot(List<Transaction>)`, `getProof(String txId)`,
  `verifyProof(MerkleProof, String root, String txId)`.

---

### Added — Milestone 3: Storage

- **`InMemoryStorage`** — `LinkedHashMap`-backed; `ReadWriteLock`; for testing/demos.
- **`LevelDBStorage`** — crash-safe persistent storage; keys = block index as big-endian
  bytes; values = JSON via `BlockSerializer`. Verifies block hash on load (NFR-SEC-03).
- **`RocksDBStorage`** — high-throughput persistent storage via `rocksdbjni`.
- **`FileSystemStorage`** — one JSON file per block; zero native library dependency;
  path-traversal sanitized and suppressed via `@SuppressFBWarnings(PATH_TRAVERSAL_IN)`.
- **`StorageContractTest`** — abstract TCK run against all four implementations.
- **`ChainExporter`** — `toJson(Blockchain)`, `fromJson(String, BlockchainStorage)`,
  `toCsv(Blockchain)` (FR-SER-02, FR-SER-03). Added in M10.

---

### Added — Milestone 4: Consensus engines

- **`ProofOfWorkEngine`** — SHA-256 mining loop; configurable `difficulty` (leading
  zero-bit count, default 4). Validates that `block.getHash()` starts with the required
  prefix. `engineName()` = `"ProofOfWork"`.
- **`DifficultyAdjuster`** — sliding-window auto-recalibration over configurable window
  size; increases or decreases difficulty by 1 bit to hit the target block time.
- **`ProofOfAuthorityEngine`** — validates that `block.getMinerAddress()` is in the
  configured `Set<String>` of authorized addresses (FR-CONS-03).
- **`RoundRobinEngine`** — deterministic slot-based; expected miner =
  `peers.get(block.getIndex() % peers.size())`. For dev/test.
- **`PBFTEngine`** — two-phase (prepare → commit) Byzantine fault-tolerant consensus;
  configurable `quorumSize`; `ViewChangeManager` for leader rotation.
- **`ConsensusSupport`** — shared `buildBlock(...)`, `isGenesis(Block)`,
  `hasConsistentIntegrity(Block)` utilities for engine implementors.
- **`ConsensusEngineContractTest`** — abstract TCK run against all four engines.

---

### Added — Milestone 5: Wallet and mempool

- **`Wallet`** — holds `ECKeyPair`; `getAddress()`, `sign(Transaction)`,
  `getBalance(Blockchain)` (scans confirmed transactions).
- **`WalletManager`** — `createWallet()`, `importWallet(String pkHex)`,
  `exportKeystore(Wallet, String password)` (AES-256-GCM), `importKeystore(String, String)`.
- **`TransactionMempool`** — `PriorityQueue` backed; `submit(Transaction)` validates
  then inserts; `getTopN(int n)` returns by priority; `evictExpired(Duration ttl)`;
  confirmed transactions auto-removed on `BlockAddedEvent`.
- **`FeeBasedPrioritizer`** — orders by `metadata.get("fee")`, descending.
- **`TimestampBasedPrioritizer`** — orders by `timestamp`, ascending (oldest first).

---

### Added — Milestone 6: Access control

- **`PermissionManager`** — RBAC with `NodeRole` (`NODE_ADMIN`, `NODE_MINER`,
  `NODE_OBSERVER`); `assignRole()`, `hasRole()`, `getRole()`.
- **`AllowlistManager`** — `Set<String>` of permitted node IDs; checked before any
  inbound message is processed (FR-AC-01).
- **`AllowlistStore`** — persists the allowlist to storage across restarts.
- **`InvitationService`** — admin-signed, time-limited `InvitationToken` (ECDSA over
  `nodeId + expiryEpoch`); `verifyInvitation(token)` checks signature + expiry (FR-AC-04/05).

---

### Added — Milestone 7: Networking (P2P)

- **`NodeServer`** / **`NodeClient`** — Netty 4.2.13 TCP server and client.
- **`MessageCodec`** — NDJSON framing over TCP; each message is one JSON line followed
  by `\n` (ADR-001 deferred gRPC to post-1.0; NDJSON chosen for zero-dependency framing).
- **`PeerManager`** — connects, heartbeats (ping/pong every 30 s), and prunes
  unresponsive peers; publishes `PeerConnectedEvent` / `PeerDisconnectedEvent`.
- **`PeerStore`** — persists known peer addresses across restarts.
- **`GossipProtocol`** — forwards transactions to `ceil(log2(n))` random peers.
- **`BlockBroadcaster`** — pushes newly mined blocks to all connected peers on
  `BlockAddedEvent`.
- **`SyncManager`** — on startup, queries all peers for chain height, requests
  missing blocks from the peer with the highest chain (FR-NET-04).
- **`ForkResolver`** — selects the chain with the greatest cumulative difficulty (FR-NET-05).
- Network SPI interfaces (`NodeServerLifecycle`, `PeerManagerLifecycle`, `ChainSyncer`)
  defined in `blockchain-core` to preserve zero-dependency contract of the core module.

---

### Added — Milestone 8: Event bus and observability

- `BlockchainEventBus` wired into `Blockchain.addBlock()`, `TransactionMempool.submit()`,
  `PeerManager.connect()`, and `PeerManager.disconnect()`.
- `BlockchainNode.status()` — returns live `NodeStatus` record with `chainHeight`,
  `mempoolSize`, `peerCount`, `lastBlockTime`, `consensusEngine`.
- SLF4J log statements at `INFO`/`DEBUG`/`WARN` throughout all modules.

---

### Added — Milestone 9: Spring Boot autoconfiguration

- **`BlockchainAutoConfiguration`** — `@ConditionalOnProperty("blockchain.enabled")`;
  creates `BlockchainConfig`, `BlockchainNode`, and `TransactionMempool` beans.
  All beans are overridable via `@ConditionalOnMissingBean`.
- **`BlockchainProperties`** — `@ConfigurationProperties(prefix = "blockchain")`
  with nested `Mempool` section; full IDE autocomplete metadata generated.
- **`BlockchainHealthIndicator`** — exposes `chain_height` and `mempool_size` via
  Spring Boot Actuator health endpoint.
- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  registered for Spring Boot 4.x autoconfiguration discovery.

---

### Added — Milestone 10: Examples, documentation, and release

- **`MoneyTransferTransaction`** (`examples/simple-chain`) — custom `Transaction`
  subtype with `currency` field bound into the ECDSA signature. Canonical AC-09
  (JSON round-trip) proof.
- **`SimpleChainDemo`** (`examples/simple-chain`) — 10-step runnable demo: wallet
  creation, tx signing, PoW mining, chain validation, `ChainExporter` round-trip, balance.
- **`VotingConsensusEngine`** (`examples/custom-consensus`) — custom `ConsensusEngine`
  requiring a strict majority of validator votes. Demonstrates FR-CONS-06 and AC-08.
- **`VotingConsensusDemo`** (`examples/custom-consensus`) — three scenarios: success,
  quorum failure, and engine name in `NodeStatus`.
- **`SpringChainApp`** + **`BlockchainRestController`** (`examples/spring-boot-demo`) —
  Spring Boot REST API: `GET /api/status`, `GET /api/chain`, `GET /api/chain/{index}`,
  `POST /api/transactions`, `POST /api/mine`. Demonstrates AC-10 (zero-config Spring bean).
- `PerformanceTest` — `isChainValid()` on 10,000 blocks verified under 2 s (T-078).
- `PrivateKeyLeakTest` — regression guard: `toString()`, JUL logs, and Jackson serialization
  verified to not expose the private key hex (NFR-SEC-01) (T-079).
- `package-info.java` added to all 8 packages in `blockchain-core` (T-076).
- README completely rewritten with 5-minute quick-start guide, module coordinates,
  and extension examples (T-077).
- `CHANGELOG.md` created (this file) (T-080).
- `maven-release-plugin 3.1.1` configured; `distributionManagement` updated to
  GitHub Packages (T-080).
- Aggregate Javadoc published to GitHub Pages via `javadoc.yml` workflow (T-081).

---

### Fixed

- `Blockchain.java` — wrong `SuppressFBWarnings` import
  (`org.apache.logging.log4j.internal.annotation` → `edu.umd.cs.findbugs.annotations`).
  The internal Log4j annotation class is not on the classpath and caused SpotBugs
  and Qodana failures.
- `scm` section in root `pom.xml` — updated placeholder `your-org` to actual GitHub
  username `dhruv0306`.
- `distributionManagement` in root `pom.xml` — updated from OSSRH staging URLs
  (Maven Central) to GitHub Packages for correct deployment target.

---

### Security

All CVE findings from the Mend.io dependency scan addressed (see `owasp-suppressions.xml`
for suppressed findings with documented justification):

| Dependency        | Previous | Patched | CVE(s) fixed                                                   |
|-------------------|----------|---------|----------------------------------------------------------------|
| `jackson-core`    | 2.17.1   | 2.21.1  | WS-2026-0003 (CVSS 7.5)                                        |
| `bcprov-jdk18on`  | 1.78.1   | 1.84    | CVE-2026-0636 (CVSS 5.3)                                       |
| `netty-*`         | 4.1.111  | 4.2.13  | CVE-2025-58057, -58056, CVE-2026-33870, -33871, CVE-2025-24970 |
| `tomcat-embed-*`  | 10.1.x   | 10.1.55 | CVE-2026-29145 (CVSS 9.1), -29129, -29146, -34483              |
| `protobuf-java`   | 3.25.3   | 4.28.2  | CVE-2024-7254 (CVSS 7.5)                                       |
| `spring-boot`     | 3.3.0    | 4.0.6   | CVE-2026-22733 (CVSS 8.2), CVE-2025-22235                      |
| `logback-classic` | 1.5.6    | 1.5.25  | CVE-2025-11226, CVE-2024-12798, CVE-2026-1225, CVE-2024-12801  |

---

### Known limitations (post-1.0 backlog)

| Backlog ID | Description                                                                            |
|------------|----------------------------------------------------------------------------------------|
| T-B02      | gRPC transport (ADR-001). Current transport: NDJSON over plain TCP.                    |
| T-B03      | `blockchain-metrics` — Micrometer module for Prometheus/Grafana dashboards.            |
| T-B04      | Chain snapshot / checkpoint for fast node bootstrap on long chains.                    |
| T-B05      | WebSocket block-explorer API.                                                          |
| T-B06      | Docker Compose 3-node demo network.                                                    |
| —          | `FR-NET-06` (Protocol Buffers P2P framing) deferred; NDJSON framing used.              |
| —          | `FR-WALLET-03` (Web3 Secret Storage v3 keystore) deferred; AES-256-GCM used.           |
| —          | `PBFTEngine` quorum operates in-process only; distributed PBFT needs networking layer. |

---

[1.0.0]: https://github.com/dhruv0306/private-blockchain/releases/tag/v1.0.0
