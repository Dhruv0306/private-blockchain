# Tasks — private-blockchain Maven library

**Version:** 1.0.0-SNAPSHOT  
**Methodology:** Milestone-based, each milestone is independently shippable  
**Last updated:** 2026-04-22

---

## Task notation

- `[P0]` — blocker; nothing in the milestone ships without it
- `[P1]` — required for milestone completion
- `[P2]` — nice-to-have; deferred to next milestone if needed
- `[SPIKE]` — time-boxed research task (max 2 days), produces a decision record
- Each task references the requirement ID(s) it satisfies

---

## Milestone 0 — Project scaffold (1 week)

Goal: empty but buildable multi-module Maven project, CI green, code-quality gates in place.

| ID | Priority | Task | Requirement(s) | Notes |
|---|---|---|---|---|
| T-001 | P0 | Create root `pom.xml` with `<modules>` listing all 9 sub-modules | — | Use `maven-wrapper` (`mvnw`) |
| T-002 | P0 | Create module POMs for: `blockchain-core`, `blockchain-crypto`, `blockchain-consensus`, `blockchain-network`, `blockchain-storage`, `blockchain-wallet`, `blockchain-access`, `blockchain-mempool`, `blockchain-spring`, `blockchain-examples` | — | All inherit from root POM |
| T-003 | P0 | Configure `maven-compiler-plugin` for Java 17 source/target | NFR-01 | Enable `--enable-preview` as optional profile |
| T-004 | P1 | Configure Checkstyle with Google Java Style (max line length 120) | NFR-10 | Fail build on violation |
| T-005 | P1 | Configure SpotBugs; fail on HIGH and CRITICAL findings | NFR-10 | Exclude generated code |
| T-006 | P1 | Configure JaCoCo; enforce 80% line coverage on `blockchain-core` | NFR-05 | Coverage report to `target/site` |
| T-007 | P1 | Set up GitHub Actions workflow: `build.yml` (compile + test on push), `release.yml` (publish to GitHub Packages on tag) | — | Matrix: JDK 17 and JDK 21 |
| T-008 | P2 | Add `.editorconfig`, `.gitignore`, `CONTRIBUTING.md`, `LICENSE` (Apache 2.0) | — | — |
| T-009 | P2 | Add issue templates: `bug_report.yml`, `feature_request.yml` | — | — |

---

## Milestone 1 — Core data model (2 weeks)

Goal: `blockchain-core` compiles; `Block`, `Transaction`, `Blockchain`, and all SPI interfaces exist with full Javadoc; serialization round-trips pass.

### Model classes

| ID | Priority | Task | Requirement(s) |
|---|---|---|---|
| T-010 | P0 | Implement `BlockHeader` record: `version`, `bits`, `nonce`, `merkleRoot`, `timestamp` | FR-01 |
| T-011 | P0 | Implement `Block` immutable class using `BlockHeader` + `List<Transaction>`; compute `hash` via `HashUtil` on construction | FR-01 |
| T-012 | P0 | Implement abstract `Transaction` base class with all specified fields; annotate with Jackson `@JsonTypeInfo` for polymorphic subtype handling | FR-02, NFR-04 |
| T-013 | P0 | Implement `Blockchain` class: `addBlock()`, `isChainValid()`, `getBlock()`, `getLatestBlock()` | FR-03, FR-04 |
| T-014 | P1 | Write unit tests: `BlockTest`, `TransactionTest`, `BlockchainTest` covering creation, valid chain, tampered-chain detection | FR-03, FR-04 |

### SPI interfaces

| ID | Priority | Task | Requirement(s) |
|---|---|---|---|
| T-015 | P0 | Define `ConsensusEngine` interface | FR-07 |
| T-016 | P0 | Define `TransactionValidator` interface and `ValidationResult` value class | FR-12 |
| T-017 | P0 | Define `BlockchainStorage` interface | FR-29 |
| T-018 | P0 | Define `BlockchainEventListener` interface and all event classes | FR-38 |
| T-019 | P1 | Define `TransactionPrioritizer` interface | FR-22 |

### Configuration and bootstrap

| ID | Priority | Task | Requirement(s) |
|---|---|---|---|
| T-020 | P1 | Implement `BlockchainConfig` with fluent builder | FR-35 |
| T-021 | P1 | Implement `GenesisBlockFactory` | FR-37 |
| T-022 | P1 | Implement `BlockchainNode` skeleton: constructor accepts config, `start()` / `stop()` lifecycle stubs | FR-36 |

### Serialization

| ID | Priority | Task | Requirement(s) |
|---|---|---|---|
| T-023 | P1 | Implement `BlockSerializer` and `TransactionSerializer` using Jackson; ensure `Transaction` subtypes round-trip | NFR-04 |
| T-024 | P1 | Write serialization round-trip tests for `Block` and a sample `Transaction` subtype | NFR-04 |

---

## Milestone 2 — Cryptography module (1 week)

Goal: `blockchain-crypto` compiles; all crypto utilities implemented and tested; no plaintext key leakage.

| ID | Priority | Task | Requirement(s) |
|---|---|---|---|
| T-025 | P0 | Add Bouncy Castle dependency (`bcprov-jdk18on`) to `blockchain-crypto` only | FR-18 |
| T-026 | P0 | Implement `HashUtil`: `sha256(String)`, `sha256(byte[])`, `sha3_256(String)`, `doubleHash(String)` | FR-15 |
| T-027 | P0 | Implement `KeyPairGenerator`: generate `ECKeyPair` on `secp256k1`, export/import hex | FR-17 |
| T-028 | P0 | Implement `ECDSASignatureUtil`: `sign(byte[], PrivateKey)`, `verify(byte[], byte[], PublicKey)` | FR-16 |
| T-029 | P1 | Implement `AddressUtil`: derive address from public key (SHA-256 → RIPEMD-160 → Base58Check) | FR-19 |
| T-030 | P1 | Implement `MerkleTree`: `buildRoot(List<Transaction>)`, `getProof(String txId)`, `verifyProof(...)` | FR-05, FR-06 |
| T-031 | P1 | Override `ECKeyPair.toString()` to mask private key | NFR-09 |
| T-032 | P1 | Unit tests: `HashUtilTest`, `SignatureUtilTest`, `MerkleTreeTest`, `KeyPairTest` | NFR-05 |

---

## Milestone 3 — Storage implementations (1 week)

Goal: all three `BlockchainStorage` implementations pass a shared `StorageContractTest` abstract test class.

| ID | Priority | Task | Requirement(s) |
|---|---|---|---|
| T-033 | P0 | Implement `InMemoryStorage`: `HashMap<Integer, Block>`, thread-safe with `ReadWriteLock` | FR-30, NFR-03 |
| T-034 | P1 | Implement `LevelDBStorage`: wrap `leveldbjni-all`; keys are block index as big-endian bytes; values are JSON | FR-30 |
| T-035 | P1 | Implement `FileSystemStorage`: one JSON file per block in a configurable directory | FR-30 |
| T-036 | P1 | Write `StorageContractTest` abstract class with parameterized tests; run against all three implementations | FR-29, NFR-05 |
| T-037 | P2 | Implement `RocksDBStorage` (add `rocksdbjni` dependency) | FR-30 |

---

## Milestone 4 — Consensus engines (2 weeks)

Goal: all four consensus engines pass their integration tests; custom engine can be injected via config.

| ID | Priority | Task | Requirement(s) |
|---|---|---|---|
| T-038 | P0 | Implement `ProofOfWorkEngine`: SHA-256 mining loop with configurable `difficulty` (leading-zero count) | FR-08 |
| T-039 | P0 | Implement `ProofOfAuthorityEngine`: validate that `block.minerAddress` is in `Set<String> authorizedAddresses` | FR-08 |
| T-040 | P1 | Implement `RoundRobinEngine`: miner index = `block.index % peers.size()` | FR-08 |
| T-041 | P1 | Implement `PBFTEngine`: two-phase commit (prepare → commit) with configurable `quorumSize` | FR-10 |
| T-042 | P1 | Write `ConsensusEngineContractTest` abstract class; run against all four engines | FR-07 |
| T-043 | P1 | Wire `ConsensusEngine` into `Blockchain.addBlock()` via `BlockchainConfig` | FR-09 |
| T-044 | P1 | Write integration test: inject a custom no-op `ConsensusEngine` via config and verify it is called | FR-09 |

---

## Milestone 5 — Wallet and mempool (1 week)

| ID | Priority | Task | Requirement(s) |
|---|---|---|---|
| T-045 | P0 | Implement `Wallet`: key management, `getAddress()`, `sign(Transaction)`, `getBalance(Blockchain)` | FR-19 |
| T-046 | P1 | Implement `WalletManager`: create, import, list; persist to AES-256-GCM encrypted JSON keystore | FR-20 |
| T-047 | P1 | Implement `TransactionMempool`: `PriorityQueue` backed, TTL eviction via `ScheduledExecutorService` | FR-21 |
| T-048 | P1 | Implement `FeeBasedPrioritizer` and `TimestampPrioritizer` | FR-22 |
| T-049 | P1 | Implement mempool validator gate: reject transactions failing `TransactionValidator` | FR-23 |
| T-050 | P1 | Unit tests: `WalletTest`, `WalletManagerTest`, `MempoolTest` | NFR-05 |

---

## Milestone 6 — Access control (1 week)

| ID | Priority | Task | Requirement(s) |
|---|---|---|---|
| T-051 | P0 | Implement `PermissionManager`: RBAC with roles `NODE_ADMIN`, `NODE_MINER`, `NODE_OBSERVER`; role assignments persisted in `BlockchainStorage` | FR-32 |
| T-052 | P0 | Implement `AllowlistManager`: `Set<String>` of permitted addresses; checked before any inbound message is processed | FR-33 |
| T-053 | P1 | Implement `InvitationService`: admin signs an invitation token (ECDSA over `nodeId + expiry`); invitee presents token to `AllowlistManager` | FR-34 |
| T-054 | P1 | Wire `AllowlistManager` into `NodeServer` message handler as a filter | FR-33 |
| T-055 | P1 | Unit tests: `PermissionManagerTest`, `AllowlistManagerTest`, `InvitationServiceTest` | NFR-05 |

---

## Milestone 7 — Networking and P2P (2 weeks)

| ID | Priority | Task | Requirement(s) |
|---|---|---|---|
| T-056 | P0 | Add Netty 4.x dependency to `blockchain-network` | FR-24 |
| T-057 | P0 | Define `Peer` value class: `nodeId`, `host`, `port`, `publicKey`, `lastSeen` | FR-24 |
| T-058 | P0 | Implement `NodeServer`: Netty `ServerBootstrap`; dispatches inbound messages to registered handlers | FR-24 |
| T-059 | P1 | Implement `PeerManager`: connect, disconnect, heartbeat ping/pong every 30s | FR-25 |
| T-060 | P1 | Implement `BlockBroadcaster`: on `BlockAddedEvent`, serialise block and push to all connected peers | FR-26 |
| T-061 | P1 | Implement `GossipProtocol`: on `TransactionSubmittedEvent`, forward to `ceil(log2(n))` random peers | FR-27 |
| T-062 | P1 | Implement `SyncManager`: on startup compare `chain.size()` with peers; request missing blocks from the peer with the highest chain | FR-28 |
| T-063 | P1 | Integration test: two in-process nodes, mine a block on node 1, verify node 2 receives it via `BlockBroadcaster` | FR-26 |
| T-064 | P2 | [SPIKE] Evaluate gRPC as alternative transport to Netty raw TCP; produce decision record `docs/decisions/ADR-001-transport.md` | FR-24 |

---

## Milestone 8 — Event bus and observability (1 week)

| ID | Priority | Task | Requirement(s) |
|---|---|---|---|
| T-065 | P0 | Implement `BlockchainEventBus`: publish/subscribe backed by `CopyOnWriteArrayList<BlockchainEventListener>` | FR-38 |
| T-066 | P1 | Wire `BlockchainEventBus` into `Blockchain.addBlock()`, `TransactionMempool.submit()`, `PeerManager.connect()` | FR-38, FR-39 |
| T-067 | P1 | Add SLF4J log statements at appropriate levels across all modules | NFR-06 |
| T-068 | P2 | Add Micrometer integration (optional module `blockchain-metrics`) for JVM metrics and a `chain_height` gauge | NFR-06 |

---

## Milestone 9 — Spring Boot autoconfigure module (1 week)

| ID | Priority | Task | Requirement(s) |
|---|---|---|---|
| T-069 | P0 | Add Spring Boot 3.x BOM to root POM (scope `import`) | FR-40 |
| T-070 | P0 | Implement `BlockchainAutoConfiguration`: `@ConditionalOnProperty("blockchain.enabled")`, creates `BlockchainConfig`, `BlockchainNode`, and `TransactionMempool` beans | FR-40 |
| T-071 | P1 | Expose configuration properties via `@ConfigurationProperties("blockchain")` and generate metadata for IDE autocomplete | FR-40 |
| T-072 | P1 | Verify all auto-configured beans are overridable via `@ConditionalOnMissingBean` | FR-41 |
| T-073 | P1 | Write Spring integration test: load application context, assert `BlockchainNode` bean exists and is started | FR-40 |

---

## Milestone 10 — Examples, docs, and release (1 week)

| ID | Priority | Task | Requirement(s) |
|---|---|---|---|
| T-074 | P0 | Write `blockchain-examples/SimpleChainExample.java`: genesis block, custom transaction subclass, custom consensus engine, chain validation | FR-11, FR-09 |
| T-075 | P0 | Write `blockchain-examples/SpringBootDemo`: Spring Boot app using `blockchain-spring` autoconfigure | FR-40 |
| T-076 | P1 | 100% Javadoc coverage on all public APIs in `blockchain-core` | NFR-07 |
| T-077 | P1 | Write `README.md` with quick-start (5-minute guide), Maven coordinates, and extension examples | — |
| T-078 | P1 | Performance test: validate chain of 10,000 blocks in under 2 seconds | NFR-08 |
| T-079 | P1 | Security audit: verify no private key in logs or `toString()` across all modules | NFR-09 |
| T-080 | P1 | `mvn release:prepare release:perform` to tag `1.0.0` and publish to GitHub Packages | — |
| T-081 | P2 | Publish Javadoc site to GitHub Pages via `maven-javadoc-plugin` + `maven-site-plugin` | — |

---

## Backlog (post-1.0)

| ID | Task | Notes |
|---|---|---|
| T-B01 | Implement `RocksDBStorage` | High-throughput write scenario |
| T-B02 | gRPC transport implementation | See ADR-001 |
| T-B03 | `blockchain-metrics` Micrometer module | Operational dashboards |
| T-B04 | Chain snapshot / checkpoint support | Fast node bootstrap |
| T-B05 | WebSocket-based block explorer API | Developer tooling |
| T-B06 | Docker Compose example with 3-node network | Demo and testing |

---

## Dependency graph of milestones

```
M0 (scaffold)
  └── M1 (core model)
        ├── M2 (crypto)         ← M1 requires HashUtil stub
        ├── M3 (storage)        ← M1 defines BlockchainStorage SPI
        ├── M4 (consensus)      ← M1 + M2 + M3
        │     └── M5 (wallet/mempool)   ← M4 + M2
        │           └── M6 (access control)  ← M5
        │                 └── M7 (networking) ← M6
        │                       └── M8 (events) ← all
        │                             └── M9 (spring) ← M8
        │                                   └── M10 (release) ← all
```
