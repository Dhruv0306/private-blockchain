# private-blockchain

> A Java 17+ Maven library for building extensible, permission (private) blockchain networks.  
> Plug in your own consensus logic, transaction types, and storage backend — ship in minutes.

[![Build Status](https://github.com/dhruv0306/private-blockchain/actions/workflows/build.yml/badge.svg)](https://github.com/dhruv0306/private-blockchain/actions/workflows/build.yml)
[![Javadoc](https://img.shields.io/badge/Javadoc-1.0.0-blue)](https://dhruv0306.github.io/private-blockchain/apidocs/)
[![License](https://img.shields.io/badge/license-Apache%202.0-green.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17%2B-orange)](https://adoptium.net/)

---

## Quick start — 5 minutes to your first private chain

### 1. Add the dependencies

```xml
<!-- Minimum viable set: core + one consensus engine + one storage backend -->
<dependency>
    <groupId>com.privatechain</groupId>
    <artifactId>blockchain-core</artifactId>
    <version>1.0.0</version>
</dependency>
<dependency>
<groupId>com.privatechain</groupId>
<artifactId>blockchain-consensus</artifactId>
<version>1.0.0</version>
</dependency>
<dependency>
<groupId>com.privatechain</groupId>
<artifactId>blockchain-storage</artifactId>
<version>1.0.0</version>
</dependency>
```

### 2. Start a node in ≤ 10 lines

```java
// 1. Build and start
BlockchainNode node = BlockchainConfig.builder()
                .consensusEngine(new ProofOfWorkEngine(4))   // 4 leading zero bits
                .storage(new LevelDBStorage("/data/chain"))  // swap for InMemoryStorage in tests
                .chainId("my-chain")
                .build();
node.

start();

// 2. Inspect
BlockchainNode.NodeStatus status = node.status();
System.out.

println("Height: "+status.chainHeight());    // 1 (genesis)
        System.out.

println("Engine: "+status.consensusEngine()); // "ProofOfWork"
        System.out.

println("Mempool: "+status.mempoolSize());     // 0

// 3. Stop
        node.

stop();
```

### 3. Define a custom transaction type

Extend `Transaction` and annotate with `@JsonCreator` / `@JsonProperty` for automatic
JSON round-trips — no registry, no registration step required.

```java
public final class PaymentTx extends Transaction {

    private final String currency; // included in ECDSA signature

    @JsonCreator
    public PaymentTx(
            @JsonProperty("id") UUID id,
            @JsonProperty("senderAddress") String senderAddress,
            @JsonProperty("receiverAddress") String receiverAddress,
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("metadata") Map<String, Object> metadata,
            @JsonProperty("currency") String currency) {

        super(id, senderAddress, receiverAddress, amount, timestamp, metadata);
        this.currency = Objects.requireNonNull(currency);
    }

    // Bind currency into the signature — prevents post-signing substitution
    @Override
    public byte[] toSignableBytes() {
        byte[] base = super.toSignableBytes();
        byte[] ext = ("|" + currency).getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[base.length + ext.length];
        System.arraycopy(base, 0, out, 0, base.length);
        System.arraycopy(ext, 0, out, base.length, ext.length);
        return out;
    }

    public String getCurrency() {
        return currency;
    }
}
```

### 4. Sign and submit transactions

```java
// Create wallets
WalletManager wm = new WalletManager();
Wallet alice = wm.createWallet();
Wallet bob = wm.createWallet();

// Build, sign, and submit a transaction
PaymentTx tx = new PaymentTx(
        UUID.randomUUID(), alice.getAddress(), bob.getAddress(),
        new BigDecimal("50.00"), Instant.now(), null, "USD");
alice.

sign(tx);
node.

submitTransaction(tx);  // validates → mempool → TransactionSubmittedEvent
```

### 5. Mine a block

```java
List<Transaction> pending = node.getMempool().getTopN(100);
Block mined = node.getChain()
        .getConsensusEngine()
        .mineBlock(pending, node.getChain().getLatestBlock());
node.

getChain().

addBlock(mined); // validates → persists → BlockAddedEvent
System.out.

println("Block #"+mined.getIndex() +": "+mined.

getHash());
        System.out.

println("Chain valid: "+node.getChain().

isChainValid());
```

### 6. Swap the consensus engine

```java
// Proof of Authority: only these addresses may produce blocks
Set<String> authorizedMiners = Set.of(aliceAddress, bobAddress);

BlockchainNode node = BlockchainConfig.builder()
        .consensusEngine(new ProofOfAuthorityEngine(authorizedMiners))
        .storage(new LevelDBStorage("/data/chain"))
        .build();
node.

start();
```

Or inject a fully custom engine:

```java
public class VotingEngine implements ConsensusEngine {
    @Override
    public boolean validateBlock(Block b, Blockchain c) { /* quorum check */ }

    @Override
    public Block mineBlock(List<Transaction> txs, Block prev) { /* assemble */ }

    @Override
    public String engineName() {
        return "VotingEngine";
    }
}

BlockchainNode node = BlockchainConfig.builder()
        .consensusEngine(new VotingEngine())
        .build();
```

### 7. Spring Boot autoconfiguration (zero boilerplate)

Add `blockchain-spring` and two lines of `application.yml`:

```xml

<dependency>
    <groupId>com.privatechain</groupId>
    <artifactId>blockchain-spring</artifactId>
    <version>1.0.0</version>
</dependency>
```

```yaml
blockchain:
  enabled: true
  difficulty: 4
  chain-id: my-spring-chain
```

`BlockchainNode` is created, started, and injected as a Spring bean automatically.
Override any behavior by declaring your own `ConsensusEngine` or `BlockchainStorage` bean.

### 8. Export and import the chain

```java
// Export full chain as JSON (FR-SER-02)
String json = ChainExporter.toJson(node.getChain());

// Restore into a fresh backend — e.g. for fast node bootstrap
InMemoryStorage fresh = new InMemoryStorage();
ChainExporter.

fromJson(json, fresh);

// Export as CSV for audit / analytics (FR-SER-03)
String csv = ChainExporter.toCsv(node.getChain());
// columns: blockIndex, blockHash, txId, sender, receiver, amount, timestamp, txType
```

---

## Module coordinates

All modules share **version `1.0.0`** and **group ID `com.privatechain`**.

| Artifact ID            | Depends on                                                  | Key external dep         |
|------------------------|-------------------------------------------------------------|--------------------------|
| `blockchain-core`      | JDK only                                                    | —                        |
| `blockchain-crypto`    | `blockchain-core`                                           | Bouncy Castle 1.84       |
| `blockchain-consensus` | `blockchain-core`, `blockchain-crypto`                      | —                        |
| `blockchain-storage`   | `blockchain-core`                                           | LevelDB JNI, RocksDB JNI |
| `blockchain-wallet`    | `blockchain-core`, `blockchain-crypto`                      | —                        |
| `blockchain-access`    | `blockchain-core`, `blockchain-crypto`                      | —                        |
| `blockchain-network`   | `blockchain-core`, `blockchain-crypto`, `blockchain-access` | Netty 4.2.13             |
| `blockchain-spring`    | all above                                                   | Spring Boot 4.0.6        |

### Common dependency combinations

**Core + consensus + in-memory storage (tests / prototyping)**

```xml

<dependency>
    <groupId>com.privatechain</groupId>
    <artifactId>blockchain-core</artifactId>
    <version>1.0.0</version>
</dependency>
<dependency>
<groupId>com.privatechain</groupId>
<artifactId>blockchain-consensus</artifactId>
<version>1.0.0</version>
</dependency>
<dependency>
<groupId>com.privatechain</groupId>
<artifactId>blockchain-storage</artifactId>
<version>1.0.0</version>
</dependency>
```

**Full stack with wallets and P2P networking**

```xml
<!-- Wallets, crypto, consensus, LevelDB/RocksDB, Netty P2P -->
<dependency>
    <groupId>com.privatechain</groupId>
    <artifactId>blockchain-network</artifactId>
    <version>1.0.0</version>
</dependency>
<dependency>
<groupId>com.privatechain</groupId>
<artifactId>blockchain-wallet</artifactId>
<version>1.0.0</version>
</dependency>
```

**Spring Boot application**

```xml
<!-- Brings in all modules transitively -->
<dependency>
    <groupId>com.privatechain</groupId>
    <artifactId>blockchain-spring</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

## Repository structure

```
private-blockchain/
│
├── .github/
│   ├── workflows/
│   │   ├── build.yml                    # Compiles, tests, and lints on every push and pull request
│   │   ├── release.yml                  # Publishes artifacts to GitHub Packages on a version tag
│   │   ├── javadoc.yml                  # Publishes aggregate Javadoc to GitHub Pages on main push
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
│       │   ├── model/                   # Block, BlockHeader, Transaction (abstract)
│       │   ├── spi/                     # ConsensusEngine, TransactionValidator, BlockchainStorage
│       │   ├── event/                   # BlockchainEventBus, BlockchainEvent (sealed), listener
│       │   ├── exception/               # BlockchainException hierarchy (all unchecked)
│       │   ├── mempool/                 # TransactionMempool, FeeBasedPrioritizer, TimestampPrioritizer
│       │   ├── network/                 # NodeServerLifecycle, PeerManagerLifecycle, ChainSyncer
│       │   └── builder/                 # BlockchainConfig, BlockchainNode, Blockchain, GenesisBlockFactory
│       └── test/java/com/privatechain/core/
│           ├── BlockTest.java           PerformanceTest.java  (+ 9 other test classes)
│
├── blockchain-crypto/                   # Cryptographic primitives (Bouncy Castle / secp256k1)
├── blockchain-consensus/                # PoW, PoA, PBFT, RoundRobin engines + DifficultyAdjuster
├── blockchain-storage/                  # InMemory, LevelDB, RocksDB, FileSystem + ChainExporter
├── blockchain-network/                  # Netty TCP P2P: PeerManager, SyncManager, GossipProtocol
├── blockchain-wallet/                   # Wallet, WalletManager, KeystoreSerializer
├── blockchain-access/                   # AllowlistManager, PermissionManager, InvitationService
├── blockchain-spring/                   # BlockchainAutoConfiguration + BlockchainProperties
│
├── examples/
│   ├── simple-chain/                    # SimpleChainDemo + MoneyTransferTransaction (T-074)
│   ├── custom-consensus/                # VotingConsensusEngine + VotingConsensusDemo (T-074)
│   └── spring-boot-demo/               # REST API over blockchain-spring autoconfiguration (T-075)
│
├── pom.xml                              # Parent POM; manages dependency versions and build profiles
├── qodana.yaml                          # JetBrains Qodana static analysis configuration
├── README.md
├── CHANGELOG.md
├── CONTRIBUTING.md
└── LICENSE                              # Apache License 2.0
```

---

## Built-in consensus engines

| Engine             | Class                        | Use case                                            |
|--------------------|------------------------------|-----------------------------------------------------|
| Proof of Work      | `ProofOfWorkEngine`          | General-purpose; configurable difficulty            |
| Proof of Authority | `ProofOfAuthorityEngine`     | Permissioned networks; authorized signer set        |
| PBFT               | `PBFTEngine`                 | Byzantine fault tolerance; 2f+1 quorum              |
| Round-Robin        | `RoundRobinEngine`           | Deterministic slot-based; dev/test use              |
| **Custom**         | `implements ConsensusEngine` | Any logic — inject via `BlockchainConfig.builder()` |

---

## Built-in storage backends

| Backend     | Class               | Notes                                  |
|-------------|---------------------|----------------------------------------|
| In-memory   | `InMemoryStorage`   | Ephemeral; for testing                 |
| LevelDB     | `LevelDBStorage`    | Persistent; crash-safe                 |
| RocksDB     | `RocksDBStorage`    | High write-throughput                  |
| File System | `FileSystemStorage` | One JSON file per block; no native dep |

---

## Access control

The `blockchain-access` module provides a three-layer private network gate:

```
Inbound TCP message
       │
       ▼
AllowlistManager  ──[DENY]──► drop + log (FR-AC-01)
       │ [ALLOW]
       ▼
PermissionManager ──[INSUFFICIENT ROLE]──► return error
       │ [AUTHORIZED]                       (FR-AC-02)
       ▼
Message handler (block / transaction / peer-discover)
```

Roles: `NODE_ADMIN`, `NODE_MINER`, `NODE_OBSERVER`.  
New nodes join via a time-limited ECDSA-signed `InvitationToken` issued by an admin.

---

## Events

Register any number of `BlockchainEventListener` implementations at runtime:

```java
node.getEventBus().

register(event ->{
        switch(event){
        case
BlockchainEvent.BlockAddedEvent e ->
        System.out.

println("Block added: #"+e.block().

getIndex());
        case
BlockchainEvent.TransactionSubmittedEvent e ->
        System.out.

println("TX submitted: "+e.transaction().

getId());
        case
BlockchainEvent.PeerConnectedEvent e ->
        System.out.

println("Peer connected: "+e.peer().

nodeId());
default ->{ /* handle ForkDetectedEvent, PeerDisconnectedEvent */ }
        }
        });
```

All events are delivered asynchronously from a daemon thread pool — listener execution
never delays the operation that published the event.

---

## Documentation

| Document     | Location                              | Description                                                 |
|--------------|---------------------------------------|-------------------------------------------------------------|
| Requirements | `docs/requirements.md`                | Functional and non-functional requirements (FR-01 … NFR-10) |
| Tasks        | `docs/tasks.md`                       | Milestone plan, task IDs (T-001 … T-081), priorities        |
| Design       | `docs/design.md`                      | Architecture, data-flow, and class diagrams                 |
| ADR-001      | `docs/decisions/ADR-001-transport.md` | Transport layer decision (TCP vs gRPC)                      |
| Javadoc      | [GitHub Pages][javadoc]               | Aggregate API reference for all modules                     |
| Examples     | `examples/`                           | Three runnable demos — `mvn exec:java -pl examples/<name>`  |

[javadoc]: https://dhruv0306.github.io/private-blockchain/apidocs/

---

## Building from source

```bash
# Clone
git clone https://github.com/dhruv0306/private-blockchain.git
cd private-blockchain

# Full build (compile + test + SpotBugs + JaCoCo)
mvn clean verify

# Run the simple-chain demo
mvn exec:java -pl examples/simple-chain

# Run the custom-consensus demo
mvn exec:java -pl examples/custom-consensus

# Run the Spring Boot REST demo
mvn spring-boot:run -pl examples/spring-boot-demo
# Then: curl http://localhost:8080/api/status
#       curl -X POST http://localhost:8080/api/mine

# Generate aggregate Javadoc
mvn javadoc:aggregate -pl .
open target/site/apidocs/index.html

# Run OWASP Dependency-Check (requires NVD API key)
NVD_API_KEY=your-key mvn -Psecurity verify
```

---

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md). All pull requests must pass:

- `mvn clean verify` (compile + test + SpotBugs + JaCoCo ≥ 80%)
- Checkstyle (Google Java Style, max line 120)
- Qodana static analysis (zero new findings at ERROR severity)

---

## License

Apache License 2.0 — see [`LICENSE`](LICENSE).
