# Repository Structure

```
private-blockchain/
│
├── .github/
│   ├── workflows/
│   │   ├── ci.yml                          # Build, test, lint on every PR
│   │   ├── release.yml                     # Publish to Maven Central on tag
│   │   └── dependency-check.yml            # OWASP dependency vulnerability scan
│   └── ISSUE_TEMPLATE/
│       ├── bug_report.md
│       └── feature_request.md
│
├── docs/
│   ├── requirements.md                     # Functional & non-functional requirements
│   ├── design.md                           # Architecture, dataflow, class diagrams
│   ├── tasks.md                            # Development task breakdown
│   ├── CONTRIBUTING.md                     # How to contribute
│   └── adr/                                # Architecture Decision Records
│       ├── ADR-001-multimodule-maven.md
│       ├── ADR-002-spi-extension-model.md
│       ├── ADR-003-storage-abstraction.md
│       └── ADR-004-consensus-interface.md
│
├── blockchain-core/                        # ← USER DEPENDS ON THIS
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/privatechain/core/
│       │   ├── model/
│       │   │   ├── Block.java              # Immutable block with builder
│       │   │   ├── BlockHeader.java        # Lightweight header (no txs)
│       │   │   ├── Transaction.java        # Abstract base — extend this
│       │   │   ├── TransactionId.java      # Typed UUID wrapper
│       │   │   ├── Address.java            # Node/wallet address value object
│       │   │   ├── Signature.java          # ECDSA signature value object
│       │   │   ├── MerkleProof.java        # Inclusion proof DTO
│       │   │   └── ChainMetadata.java      # Chain stats (height, difficulty, etc.)
│       │   ├── spi/
│       │   │   ├── ConsensusEngine.java    # Interface: validateBlock, mineBlock
│       │   │   ├── TransactionValidator.java # Interface: validate(tx) → Result
│       │   │   ├── BlockchainStorage.java  # Interface: save/load/exists
│       │   │   └── BlockchainEventListener.java # Interface: onBlockAdded, etc.
│       │   ├── event/
│       │   │   ├── BlockAddedEvent.java
│       │   │   ├── TransactionSubmittedEvent.java
│       │   │   ├── ForkDetectedEvent.java
│       │   │   └── BlockchainEventBus.java # Internal pub/sub bus
│       │   ├── exception/
│       │   │   ├── BlockValidationException.java
│       │   │   ├── TransactionValidationException.java
│       │   │   ├── ChainCorruptException.java
│       │   │   └── ConsensusException.java
│       │   └── util/
│       │       ├── GenesisBlockFactory.java
│       │       ├── ChainValidator.java     # Standalone chain integrity checker
│       │       └── BlockchainConfig.java   # Builder-pattern root config
│       │
│       ├── main/java/com/privatechain/
│       │   ├── Blockchain.java             # Core chain manager
│       │   ├── BlockchainNode.java         # Top-level entry point (wires all modules)
│       │   └── TransactionMempool.java     # Unconfirmed transaction pool
│       │
│       └── test/java/com/privatechain/core/
│           ├── model/BlockTest.java
│           ├── model/TransactionTest.java
│           ├── ChainValidatorTest.java
│           └── GenesisBlockFactoryTest.java
│
├── blockchain-crypto/                      # Cryptography primitives
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/privatechain/crypto/
│       │   ├── HashUtil.java               # SHA-256, SHA-3, RIPEMD-160
│       │   ├── SignatureUtil.java          # ECDSA secp256k1 sign/verify
│       │   ├── MerkleTree.java             # Build root, generate/verify proofs
│       │   ├── KeyPairGenerator.java       # EC key pair generation
│       │   └── AddressUtil.java            # Derive address from public key
│       └── test/java/com/privatechain/crypto/
│           ├── HashUtilTest.java
│           ├── SignatureUtilTest.java
│           └── MerkleTreeTest.java
│
├── blockchain-consensus/                   # Built-in consensus implementations
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/privatechain/consensus/
│       │   ├── pow/
│       │   │   ├── ProofOfWorkEngine.java  # SHA-256 mining, configurable difficulty
│       │   │   └── DifficultyAdjuster.java # Auto-adjust difficulty by block time
│       │   ├── poa/
│       │   │   ├── ProofOfAuthorityEngine.java # Authorized signer set
│       │   │   └── AuthorizedSignerRegistry.java
│       │   ├── pbft/
│       │   │   ├── PBFTEngine.java         # 3-phase: pre-prepare, prepare, commit
│       │   │   ├── PBFTMessageHandler.java
│       │   │   └── ViewChangeManager.java  # Leader rotation on failure
│       │   └── roundrobin/
│       │       └── RoundRobinEngine.java   # Simple slot-based rotation
│       └── test/...
│
├── blockchain-network/                     # P2P networking layer
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/privatechain/network/
│       │   ├── peer/
│       │   │   ├── Peer.java               # Peer value object (id, host, port, pubkey)
│       │   │   ├── PeerManager.java        # Discover, connect, heartbeat, prune
│       │   │   └── PeerStore.java          # Persistent peer address book
│       │   ├── sync/
│       │   │   ├── SyncManager.java        # Chain sync on startup / after partition
│       │   │   ├── BlockFetcher.java       # Request missing blocks from peers
│       │   │   └── ForkResolver.java       # Longest-chain fork resolution
│       │   ├── gossip/
│       │   │   ├── GossipProtocol.java     # Probabilistic message propagation
│       │   │   └── BlockBroadcaster.java   # Push new blocks to connected peers
│       │   └── rpc/
│       │       ├── NodeServer.java         # Netty / gRPC server
│       │       ├── NodeClient.java         # Outbound connection client
│       │       ├── MessageCodec.java       # Encode/decode wire messages
│       │       └── proto/
│       │           └── blockchain.proto    # gRPC/Protobuf definitions
│       └── test/...
│
├── blockchain-storage/                     # Persistence implementations
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/privatechain/storage/
│       │   ├── memory/
│       │   │   └── InMemoryStorage.java    # HashMap — testing / demo
│       │   ├── leveldb/
│       │   │   └── LevelDBStorage.java     # LevelDB via leveldbjni
│       │   ├── rocksdb/
│       │   │   └── RocksDBStorage.java     # RocksDB — high write throughput
│       │   └── fs/
│       │       └── FileSystemStorage.java  # JSON files per block
│       └── test/...
│
├── blockchain-wallet/                      # Key management & signing
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/privatechain/wallet/
│       │   ├── Wallet.java                 # Holds ECKeyPair, signs transactions
│       │   ├── WalletManager.java          # Create, import, export, list wallets
│       │   └── KeystoreSerializer.java     # Encrypted UTC/JSON keystore format
│       └── test/...
│
├── blockchain-access/                      # Private-chain access control
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/privatechain/access/
│       │   ├── rbac/
│       │   │   ├── Role.java               # Enum: ADMIN, MINER, OBSERVER, VALIDATOR
│       │   │   ├── Permission.java         # Fine-grained permission enum
│       │   │   └── PermissionManager.java  # Assign/check roles per address
│       │   ├── allowlist/
│       │   │   ├── AllowlistManager.java   # Whitelist of permitted node addresses
│       │   │   └── AllowlistStore.java     # Persistent storage for the allowlist
│       │   └── invite/
│       │       ├── InvitationService.java  # Admin signs invitation token
│       │       └── InvitationToken.java    # Signed invite DTO
│       └── test/...
│
├── blockchain-spring/                      # Optional Spring Boot autoconfiguration
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/privatechain/spring/
│       │   ├── BlockchainAutoConfiguration.java
│       │   ├── BlockchainProperties.java   # application.yml bindings
│       │   └── BlockchainHealthIndicator.java
│       └── main/resources/META-INF/
│           └── spring/
│               └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│
├── examples/
│   ├── simple-chain/                       # Minimal standalone example
│   │   ├── pom.xml
│   │   └── src/main/java/.../SimpleChainDemo.java
│   ├── spring-boot-demo/                   # Spring Boot integration example
│   │   ├── pom.xml
│   │   └── src/main/java/.../SpringChainApp.java
│   └── custom-consensus/                   # Shows ConsensusEngine implementation
│       ├── pom.xml
│       └── src/main/java/.../VotingConsensusEngine.java
│
├── pom.xml                                 # Parent POM (BOM, dependency management)
├── README.md
├── CHANGELOG.md
├── LICENSE                                 # Apache 2.0
└── .gitignore
```