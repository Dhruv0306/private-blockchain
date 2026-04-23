# private-blockchain

> A Java 17+ Maven library for building extensible, permissioned (private) blockchain networks.  
> Plug in your own consensus logic, transaction types, and storage backend.

## Quick start

```xml
<dependency>
    <groupId>com.privatechain</groupId>
    <artifactId>blockchain-core</artifactId>
    <version>1.0.0</version>
</dependency>
<!-- Add only the modules you need -->
<dependency>
    <groupId>com.privatechain</groupId>
    <artifactId>blockchain-consensus</artifactId>
    <version>1.0.0</version>
</dependency>
```

```java
BlockchainNode node = BlockchainConfig.builder()
    .consensusEngine(new ProofOfAuthorityEngine(authorizedAddresses))
    .storage(new LevelDBStorage("/data/chain"))
    .transactionValidator(new MyDomainValidator())
    .port(8545)
    .build()
    .start();
```

---

## Repository structure

```
private-blockchain/
│
├── pom.xml                          ← root multi-module POM (BOM, plugin management)
│
├── .github/
│   ├── workflows/
│   │   ├── build.yml                ← CI: compile + test on push (JDK 17 + 21 matrix)
│   │   └── release.yml              ← publish to GitHub Packages on version tag
│   └── ISSUE_TEMPLATE/
│       ├── bug_report.yml
│       └── feature_request.yml
│
├── docs/
│   ├── requirements.md              ← FR / NFR, acceptance criteria
│   ├── tasks.md                     ← milestone breakdown, task IDs, priorities
│   ├── design.md                    ← architecture, data-flow, class diagrams
│   └── decisions/
│       └── ADR-001-transport.md     ← architecture decision records
│
│── blockchain-core/                 ← ZERO external runtime dependencies
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/privatechain/core/
│       │   ├── model/
│       │   │   ├── Block.java
│       │   │   ├── BlockHeader.java
│       │   │   └── Transaction.java       ← abstract; extend this
│       │   ├── spi/
│       │   │   ├── ConsensusEngine.java   ← interface; implement this
│       │   │   ├── TransactionValidator.java
│       │   │   └── BlockchainStorage.java
│       │   ├── event/
│       │   │   ├── BlockchainEvent.java   ← sealed
│       │   │   ├── BlockchainEventBus.java
│       │   │   └── BlockchainEventListener.java
│       │   ├── exception/
│       │   │   ├── BlockValidationException.java
│       │   │   ├── ConsensusException.java
│       │   │   └── TransactionValidationException.java
│       │   └── builder/
│       │       ├── BlockchainConfig.java  ← fluent builder: wire everything here
│       │       ├── BlockchainNode.java    ← top-level entry point
│       │       └── GenesisBlockFactory.java
│       └── test/java/com/privatechain/core/
│           ├── BlockTest.java
│           ├── BlockchainTest.java
│           └── GenesisBlockFactoryTest.java
│
├── blockchain-crypto/               ← Bouncy Castle (secp256k1, SHA-3)
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/privatechain/crypto/
│       │   ├── HashUtil.java
│       │   ├── ECDSASignatureUtil.java
│       │   ├── KeyPairGenerator.java
│       │   ├── ECKeyPair.java
│       │   ├── AddressUtil.java
│       │   └── MerkleTree.java
│       └── test/java/com/privatechain/crypto/
│           ├── HashUtilTest.java
│           ├── SignatureUtilTest.java
│           └── MerkleTreeTest.java
│
├── blockchain-consensus/            ← built-in consensus engine implementations
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/privatechain/consensus/
│       │   ├── pow/
│       │   │   └── ProofOfWorkEngine.java
│       │   ├── poa/
│       │   │   └── ProofOfAuthorityEngine.java
│       │   ├── pbft/
│       │   │   ├── PBFTEngine.java
│       │   │   └── PBFTMessage.java
│       │   └── roundrobin/
│       │       └── RoundRobinEngine.java
│       └── test/java/com/privatechain/consensus/
│           ├── ProofOfWorkEngineTest.java
│           ├── ProofOfAuthorityEngineTest.java
│           └── ConsensusEngineContractTest.java  ← abstract; reused by all engines
│
├── blockchain-storage/              ← BlockchainStorage implementations
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/privatechain/storage/
│       │   ├── memory/
│       │   │   └── InMemoryStorage.java
│       │   ├── leveldb/
│       │   │   └── LevelDBStorage.java
│       │   ├── rocksdb/
│       │   │   └── RocksDBStorage.java
│       │   └── fs/
│       │       └── FileSystemStorage.java
│       └── test/java/com/privatechain/storage/
│           └── StorageContractTest.java          ← abstract; parameterized over all impls
│
├── blockchain-wallet/               ← key management and wallet
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/privatechain/wallet/
│       │   ├── Wallet.java
│       │   └── WalletManager.java
│       └── test/java/com/privatechain/wallet/
│           ├── WalletTest.java
│           └── WalletManagerTest.java
│
├── blockchain-mempool/              ← unconfirmed transaction pool
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/privatechain/mempool/
│       │   ├── TransactionMempool.java
│       │   ├── FeeBasedPrioritizer.java
│       │   └── TimestampPrioritizer.java
│       └── test/java/com/privatechain/mempool/
│           └── TransactionMempoolTest.java
│
├── blockchain-access/               ← private-chain access control (RBAC + allowlist)
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/privatechain/access/
│       │   ├── AllowlistManager.java
│       │   ├── PermissionManager.java
│       │   ├── InvitationService.java
│       │   └── NodeRole.java              ← enum: ADMIN / MINER / OBSERVER
│       └── test/java/com/privatechain/access/
│           ├── AllowlistManagerTest.java
│           └── InvitationServiceTest.java
│
├── blockchain-network/              ← P2P (Netty TCP transport)
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/privatechain/network/
│       │   ├── peer/
│       │   │   ├── Peer.java
│       │   │   └── PeerManager.java
│       │   ├── sync/
│       │   │   └── SyncManager.java
│       │   ├── gossip/
│       │   │   └── GossipProtocol.java
│       │   └── rpc/
│       │       ├── NodeServer.java
│       │       └── BlockBroadcaster.java
│       └── test/java/com/privatechain/network/
│           └── TwoNodeIntegrationTest.java
│
├── blockchain-spring/               ← optional Spring Boot 3.x autoconfigure
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/privatechain/spring/
│       │   │   └── BlockchainAutoConfiguration.java
│       │   └── resources/META-INF/
│       │       └── spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
│       └── test/java/com/privatechain/spring/
│           └── BlockchainAutoConfigurationTest.java
│
└── blockchain-examples/             ← runnable demos (not published to Maven)
    ├── pom.xml
    └── src/main/java/com/privatechain/examples/
        ├── SimpleChainExample.java          ← 50-line "hello blockchain"
        ├── CustomConsensusExample.java      ← inject a custom ConsensusEngine
        ├── CustomTransactionExample.java    ← extend Transaction with new fields
        └── SpringBootDemoApplication.java   ← Spring Boot app using autoconfigure
```

---

## Module dependency summary

| Module | Depends on | Key external dep |
|---|---|---|
| `blockchain-core` | JDK only | — |
| `blockchain-crypto` | `blockchain-core` | Bouncy Castle |
| `blockchain-consensus` | `blockchain-core`, `blockchain-crypto` | — |
| `blockchain-storage` | `blockchain-core` | LevelDB JNI, RocksDB JNI |
| `blockchain-wallet` | `blockchain-core`, `blockchain-crypto` | — |
| `blockchain-mempool` | `blockchain-core` | — |
| `blockchain-access` | `blockchain-core`, `blockchain-crypto` | — |
| `blockchain-network` | `blockchain-core`, `blockchain-crypto`, `blockchain-access` | Netty 4.x |
| `blockchain-spring` | all above | Spring Boot 3.x |
| `blockchain-examples` | all above | — |

---

## Documentation

| Document | Location | Description |
|---|---|---|
| Requirements | `docs/requirements.md` | Functional and non-functional requirements (FR-01 … NFR-10) |
| Tasks | `docs/tasks.md` | Milestone plan, task IDs (T-001 … T-081), priorities |
| Design | `docs/design.md` | Architecture, data-flow, and class diagrams |
| ADR-001 | `docs/decisions/ADR-001-transport.md` | Transport layer decision (TCP vs gRPC) |

---

## Contributing

See `CONTRIBUTING.md`. All changes require a passing `mvn verify` with Checkstyle and SpotBugs clean.
