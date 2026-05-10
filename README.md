# private-blockchain

> A Java 17+ Maven library for building extensible, permission (private) blockchain networks.  
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
├── .github/
│   ├── workflows/
│   │   ├── build.yml                    # Compiles, tests, and lints on every push and pull request
│   │   ├── release.yml                  # Publishes artifacts to Maven Central on a version tag
│   │   └── qodana_code_quality.yml      # Runs JetBrains Qodana static analysis
│   └── ISSUE_TEMPLATE/
│       ├── bug_report.yml               # Structured form for reporting bugs
│       └── feature_request.yml          # Structured form for proposing new features
│
├── docs/
│   ├── requirements.md                  # Functional and non-functional requirements
│   ├── design.md                        # Architecture, data-flow, and class diagrams
│   ├── tasks.md                         # Milestone breakdown with task IDs and priorities
│   └── decisions/                       # Architecture Decision Records
│       └── ADR-001-transport.md         # Transport layer selection rationale
│
├── blockchain-core/                     # Core library module — zero mandatory runtime dependencies
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/privatechain/core/
│       │   │
│       │   ├── model/                   # Immutable domain objects
│       │   │   ├── Block.java           # Immutable block containing a list of transactions
│       │   │   │                        # and a cryptographic link to the previous block
│       │   │   ├── BlockHeader.java     # Lightweight block header record holding version,
│       │   │   │                        # nonce, Merkle root, and timestamp
│       │   │   └── Transaction.java     # Abstract base class for all transaction types;
│       │   │                            # extend this to define domain-specific transactions
│       │   │
│       │   ├── spi/                     # Service Provider Interfaces — implement to extend the library
│       │   │   ├── ConsensusEngine.java         # Pluggable consensus algorithm interface;
│       │   │   │                                # defines block validation and production
│       │   │   ├── TransactionValidator.java    # Pluggable transaction validation interface;
│       │   │   │                                # returns a structured ValidationResult
│       │   │   ├── BlockchainStorage.java       # Pluggable persistence interface;
│       │   │   │                                # defines save, load, and query operations
│       │   │   ├── TransactionPrioritizer.java  # Comparator-based interface for ordering
│       │   │   │                                # transactions in the mempool
│       │   │   └── ValidationResult.java        # Immutable result of a validation check,
│       │   │                                    # carrying a status and error descriptions
│       │   │
│       │   ├── event/                   # Asynchronous publish-subscribe event system
│       │   │   ├── BlockchainEvent.java         # Sealed base class for all blockchain events;
│       │   │   │                                # contains five permitted inner event types:
│       │   │   │                                #   BlockAddedEvent
│       │   │   │                                #   TransactionSubmittedEvent
│       │   │   │                                #   PeerConnectedEvent
│       │   │   │                                #   PeerDisconnectedEvent
│       │   │   │                                #   ForkDetectedEvent
│       │   │   ├── BlockchainEventBus.java      # Thread-safe event bus that delivers events
│       │   │   │                                # asynchronously to all registered listeners
│       │   │   └── BlockchainEventListener.java # Functional interface for receiving events
│       │   │
│       │   ├── exception/               # Unchecked exception hierarchy
│       │   │   ├── BlockchainException.java          # Abstract root for all library exceptions
│       │   │   ├── BlockValidationException.java     # Thrown when a block fails validation
│       │   │   ├── ConsensusException.java           # Thrown on an unrecoverable consensus error
│       │   │   └── TransactionValidationException.java # Thrown when a transaction is rejected
│       │   │
│       │   └── builder/                 # Assembly layer — the single place where all modules are wired
│       │       ├── BlockchainConfig.java    # Fluent builder that assembles a fully configured node;
│       │       │                           # ships with default in-memory and no-op implementations
│       │       ├── BlockchainNode.java      # Top-level entry point for the library; manages the
│       │       │                           # node lifecycle and exposes the primary public API
│       │       ├── Blockchain.java          # Chain manager responsible for block appending,
│       │       │                           # integrity verification, and storage delegation
│       │       └── GenesisBlockFactory.java # Creates the deterministic genesis block for a chain
│       │
│       └── test/java/com/privatechain/core/
│           ├── BlockTest.java               # Tests for block construction, hashing, and linkage
│           ├── BlockHeaderTest.java         # Tests for header validation and builder paths
│           ├── BlockchainTest.java          # Tests for chain management and event publication
│           ├── BlockchainNodeExtTest.java   # Tests for node lifecycle, validator chain, and config
│           ├── BlockchainEventTest.java     # Tests for all event types and their accessors
│           ├── BlockchainEventBusTest.java  # Tests for async delivery, isolation, and shutdown
│           ├── GenesisBlockFactoryTest.java # Tests for genesis block structure and determinism
│           ├── TransactionTest.java         # Tests for signing, immutability, and equality
│           ├── ValidationResultTest.java    # Tests for all factory methods and status values
│           └── ExceptionTest.java          # Tests for exception constructors and accessors
│
├── blockchain-crypto/                   # Cryptographic primitives module
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/privatechain/crypto/
│       │   ├── HashUtil.java            # SHA-256, SHA-3, and double-hash utilities returning hex strings
│       │   ├── ECDSASignatureUtil.java  # ECDSA signing and verification over secp256k1
│       │   ├── KeyPairGenerator.java    # Generates elliptic curve key pairs
│       │   ├── ECKeyPair.java           # Immutable key pair record; masks the private key in toString
│       │   ├── AddressUtil.java         # Derives a blockchain address from a public key
│       │   └── MerkleTree.java          # Builds Merkle roots and generates inclusion proofs
│       └── test/java/com/privatechain/crypto/
│           ├── HashUtilTest.java
│           ├── SignatureUtilTest.java
│           ├── MerkleTreeTest.java
│           └── KeyPairTest.java
│
├── blockchain-consensus/                # Built-in consensus engine implementations
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/privatechain/consensus/
│       │   ├── pow/
│       │   │   ├── ProofOfWorkEngine.java      # Hash-based mining with configurable difficulty
│       │   │   └── DifficultyAdjuster.java     # Recalibrates difficulty based on observed block time
│       │   ├── poa/
│       │   │   ├── ProofOfAuthorityEngine.java # Accepts blocks only from a configured signer set
│       │   │   └── AuthorizedSignerRegistry.java # Manages the set of authorised signer addresses
│       │   ├── pbft/
│       │   │   ├── PBFTEngine.java             # Three-phase Byzantine fault-tolerant consensus
│       │   │   ├── PBFTMessageHandler.java     # Handles pre-prepare, prepare, and commit messages
│       │   │   └── ViewChangeManager.java      # Manages leader rotation on failure
│       │   └── roundrobin/
│       │       └── RoundRobinEngine.java       # Deterministic slot-based block production for testing
│       └── test/java/com/privatechain/consensus/
│           ├── ProofOfWorkEngineTest.java
│           ├── ProofOfAuthorityEngineTest.java
│           └── ConsensusEngineContractTest.java  # Abstract contract test executed against all engines
│
├── blockchain-storage/                  # BlockchainStorage implementations
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/privatechain/storage/
│       │   ├── memory/
│       │   │   └── InMemoryStorage.java        # HashMap-backed storage for testing and demos
│       │   ├── leveldb/
│       │   │   └── LevelDBStorage.java         # Crash-safe persistent storage backed by LevelDB
│       │   ├── rocksdb/
│       │   │   └── RocksDBStorage.java         # High write-throughput persistent storage via RocksDB
│       │   └── fs/
│       │       └── FileSystemStorage.java      # One JSON file per block; requires no native libraries
│       └── test/java/com/privatechain/storage/
│           └── StorageContractTest.java        # Abstract contract test parameterized over all implementations
│
├── blockchain-access/                   # Private-chain access control module
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/privatechain/access/
│       │   ├── rbac/
│       │   │   ├── Role.java                   # Enumeration of available node roles
│       │   │   ├── Permission.java             # Fine-grained permission definitions
│       │   │   └── PermissionManager.java      # Assigns and enforces roles per node address
│       │   ├── allowlist/
│       │   │   ├── AllowlistManager.java       # Enforces the set of permitted node identifiers
│       │   │   └── AllowlistStore.java         # Persists the allowlist across restarts
│       │   └── invite/
│       │       ├── InvitationService.java      # Generates signed, time-limited invitation tokens
│       │       └── InvitationToken.java        # Immutable signed invitation token value object
│       └── test/java/com/privatechain/access/
│           ├── AllowlistManagerTest.java
│           ├── PermissionManagerTest.java
│           └── InvitationServiceTest.java
│
├── blockchain-network/                  # Peer-to-peer networking module
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/privatechain/network/
│       │   ├── peer/
│       │   │   ├── Peer.java                   # Value object representing a remote peer
│       │   │   ├── PeerManager.java            # Manages connections, heartbeats, and peer pruning
│       │   │   └── PeerStore.java              # Persists known peer addresses across restarts
│       │   ├── sync/
│       │   │   ├── SyncManager.java            # Synchronises the local chain with the network
│       │   │   ├── BlockFetcher.java           # Requests missing blocks from a remote peer
│       │   │   └── ForkResolver.java           # Selects the canonical chain when a fork is detected
│       │   ├── gossip/
│       │   │   ├── GossipProtocol.java         # Propagates transactions to a random subset of peers
│       │   │   └── BlockBroadcaster.java       # Pushes newly produced blocks to all connected peers
│       │   └── rpc/
│       │       ├── NodeServer.java             # TCP server for inbound peer connections
│       │       ├── NodeClient.java             # Manages outbound connections to remote peers
│       │       ├── MessageCodec.java           # Encodes and decodes wire-protocol messages
│       │       └── proto/
│       │           └── blockchain.proto        # Protobuf message definitions for peer communication
│       └── test/java/com/privatechain/network/
│           └── TwoNodeIntegrationTest.java     # Verifies block propagation between two in-process nodes
│
├── blockchain-wallet/                   # Key management and transaction signing module
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/privatechain/wallet/
│       │   ├── Wallet.java                     # Holds a key pair, derives an address, and signs transactions
│       │   ├── WalletManager.java              # Creates, imports, exports, and lists wallets
│       │   └── KeystoreSerializer.java         # Encrypts and decrypts wallets using a standard keystore format
│       └── test/java/com/privatechain/wallet/
│           ├── WalletTest.java
│           └── WalletManagerTest.java
│
├── blockchain-spring/                   # Optional Spring Boot autoconfiguration module
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/privatechain/spring/
│       │   ├── BlockchainAutoConfiguration.java    # Auto-configures a BlockchainNode as a Spring bean
│       │   ├── BlockchainProperties.java           # Binds application properties to node configuration
│       │   └── BlockchainHealthIndicator.java      # Exposes chain health via Spring Boot Actuator
│       └── main/resources/META-INF/spring/
│           └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│
├── examples/                            # Runnable demonstration applications — not published to Maven
│   ├── simple-chain/
│   │   ├── pom.xml
│   │   └── src/main/java/.../SimpleChainDemo.java      # Minimal end-to-end blockchain example
│   ├── spring-boot-demo/
│   │   ├── pom.xml
│   │   └── src/main/java/.../SpringChainApp.java       # Spring Boot application using autoconfigure
│   └── custom-consensus/
│       ├── pom.xml
│       └── src/main/java/.../VotingConsensusEngine.java # Example of a custom ConsensusEngine implementation
│
├── pom.xml          # Parent POM; manages dependency versions, plugin configuration, and build profiles
├── qodana.yaml      # JetBrains Qodana static analysis configuration
├── README.md        # Project overview, quick-start guide, and module dependency table
├── CHANGELOG.md     # Version history and notable changes per release
├── CONTRIBUTING.md  # Contribution guidelines and local development setup
├── LICENSE          # Apache License 2.0
└── .gitignore
```

---

## Module dependency summary

| Module                 | Depends on                                                  | Key external dep         |
|------------------------|-------------------------------------------------------------|--------------------------|
| `blockchain-core`      | JDK only                                                    | —                        |
| `blockchain-crypto`    | `blockchain-core`                                           | Bouncy Castle            |
| `blockchain-consensus` | `blockchain-core`, `blockchain-crypto`                      | —                        |
| `blockchain-storage`   | `blockchain-core`                                           | LevelDB JNI, RocksDB JNI |
| `blockchain-wallet`    | `blockchain-core`, `blockchain-crypto`                      | —                        |
| `blockchain-mempool`   | `blockchain-core`                                           | —                        |
| `blockchain-access`    | `blockchain-core`, `blockchain-crypto`                      | —                        |
| `blockchain-network`   | `blockchain-core`, `blockchain-crypto`, `blockchain-access` | Netty 4.x                |
| `blockchain-spring`    | all above                                                   | Spring Boot 3.x          |
| `blockchain-examples`  | all above                                                   | —                        |

---

## Documentation

| Document     | Location                              | Description                                                 |
|--------------|---------------------------------------|-------------------------------------------------------------|
| Requirements | `docs/requirements.md`                | Functional and non-functional requirements (FR-01 … NFR-10) |
| Tasks        | `docs/tasks.md`                       | Milestone plan, task IDs (T-001 … T-081), priorities        |
| Design       | `docs/design.md`                      | Architecture, data-flow, and class diagrams                 |
| ADR-001      | `docs/decisions/ADR-001-transport.md` | Transport layer decision (TCP vs gRPC)                      |

---

## Contributing

See `CONTRIBUTING.md`. All changes require a passing `mvn verify` with Checkstyle and SpotBugs clean.
