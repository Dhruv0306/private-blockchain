# Design — private-blockchain Maven library

**Version:** 1.0.0-SNAPSHOT  
**Status:** Draft  
**Last updated:** 2026-04-22

---

## 1. Design philosophy

The library follows four principles:

**Open/Closed** — every significant behavior is hidden behind an interface (`ConsensusEngine`, `TransactionValidator`, `BlockchainStorage`). Library internals are closed for modification; extension points are wide open.

**Dependency inversion** — `blockchain-core` knows nothing about LevelDB, Netty, or Bouncy Castle. Those details live in their own modules. Core depends only on the JDK.

**Explicit wiring** — `BlockchainConfig` and `BlockchainNode` are the only places where modules are composed. No hidden global state, no classpath magic (outside the optional Spring autoconfigure).

**Zero-surprise serialization** — `Transaction` is abstract and polymorphic. Jackson `@JsonTypeInfo` ensures subtypes survive round-trips without any extra registration step.

---

## 2. Module dependency diagram

```
┌──────────────────────────────────────────────────────────┐
│                    blockchain-spring                       │
│              (Spring Boot autoconfigure)                   │
└────────────────────────┬─────────────────────────────────┘
                         │ depends on
┌────────────────────────▼─────────────────────────────────┐
│                   blockchain-examples                      │
└──┬──────────┬────────────┬──────────┬────────────┬───────┘
   │          │            │          │            │
   ▼          ▼            ▼          ▼            ▼
 network   consensus    storage    wallet/       access
           engines               mempool       control
   │          │            │          │            │
   └──────────┴────────────┴──────────┴────────────┘
                           │
                    ┌──────▼──────┐
                    │  blockchain  │
                    │    -crypto   │
                    └──────┬──────┘
                           │
                    ┌──────▼──────┐
                    │  blockchain  │
                    │    -core     │  (zero external deps)
                    └─────────────┘
```

Every module depends on `blockchain-core`. `blockchain-crypto` is the only module that depends on Bouncy Castle. `blockchain-network` is the only module that depends on Netty.

---

## 3. Architectural diagram

```
╔══════════════════════════════════════════════════════════════════════════╗
║                        CONSUMING APPLICATION                             ║
║  ┌──────────────────────────────────────────────────────────────────┐    ║
║  │  BlockchainConfig (builder)                                       │    ║
║  │  .consensusEngine(new MyCustomEngine())                           │    ║
║  │  .transactionValidator(new MyValidator())                         │    ║
║  │  .storage(new LevelDBStorage("/data"))                            │    ║
║  │  .build()         ──────────────────► BlockchainNode              │    ║
║  └──────────────────────────────────────────────────────────────────┘    ║
╚══════════════════════════════════════════════════════════════════════════╝
                                    │
                    ┌───────────────▼────────────────┐
                    │          BlockchainNode          │
                    │   (orchestrates all subsystems)  │
                    └──┬──────────┬──────────┬────────┘
                       │          │          │
          ┌────────────▼──┐  ┌───▼────┐  ┌──▼──────────────┐
          │  Blockchain   │  │Mempool │  │  NodeServer      │
          │  (chain mgr)  │  │        │  │  (Netty TCP)     │
          └──────┬────────┘  └───┬────┘  └──┬──────────────┘
                 │               │           │
        ┌────────▼──────┐        │    ┌──────▼────────────────┐
        │ ConsensusEngine│        │    │     PeerManager        │
        │  «interface»  │        │    │  BlockBroadcaster      │
        │  ┌──────────┐ │        │    │  GossipProtocol        │
        │  │PoW / PoA /│ │        │    │  SyncManager           │
        │  │PBFT/Custom│ │        │    └──────────────────────┘
        │  └──────────┘ │        │
        └───────────────┘        │
                 │               │
        ┌────────▼───────────────▼────────────────┐
        │         BlockchainStorage «interface»     │
        │  ┌────────────┐  ┌────────────┐  ┌────┐ │
        │  │InMemory    │  │LevelDB     │  │FS  │ │
        │  └────────────┘  └────────────┘  └────┘ │
        └─────────────────────────────────────────┘
                 │
        ┌────────▼──────────────┐
        │   BlockchainEventBus  │
        │  (publish/subscribe)  │
        └────────┬──────────────┘
                 │ notifies
        ┌────────▼──────────────────┐
        │ BlockchainEventListener   │
        │      «interface»          │
        │  (injected by consumer)   │
        └───────────────────────────┘
```

**Access control layer** sits between `NodeServer` and everything else:

```
Inbound TCP message
       │
       ▼
AllowlistManager ──[DENY]──► drop + log
       │ [ALLOW]
       ▼
PermissionManager ──[INSUFFICIENT ROLE]──► return error
       │ [AUTHORIZED]
       ▼
Message handler (Block / Transaction / PeerDiscover)
```

---

## 4. Data flow diagram

### 4.1 Transaction submission flow

```
[Client / Wallet]
       │
       │  submitTransaction(tx)
       ▼
[TransactionValidator chain]
  ├── SignatureValidator   ──FAIL──► ValidationException
  ├── BalanceValidator     ──FAIL──► ValidationException  
  └── CustomValidator(s)  ──FAIL──► ValidationException
       │ PASS
       ▼
[TransactionMempool]
  - insert into PriorityQueue (ordered by TransactionPrioritizer)
  - schedule TTL eviction
       │
       ▼
[BlockchainEventBus]  ──► onTransactionSubmitted(event)
       │
       ▼
[GossipProtocol]
  - pick ceil(log2(n)) random peers
  - serialize tx to JSON
  - push over TCP to each selected peer
```

### 4.2 Block mining and commit flow

```
[BlockProducer / miner thread]
       │
       │  selectTopN(maxTxPerBlock) from Mempool
       ▼
[ConsensusEngine.prepareBlock(txList, previousBlock)]
  - PoW: increment nonce until hash < target
  - PoA: sign block with miner's private key
  - PBFT: broadcast PREPARE, collect 2f+1 signatures
       │
       ▼ returns candidate Block
[Blockchain.addBlock(block)]
  ├── ConsensusEngine.validateBlock(block, chain)
  ├── Verify block.previousHash == chain.getLatestBlock().hash
  ├── Verify block.merkleRoot == MerkleTree.buildRoot(block.txs)
  └── BlockchainStorage.saveBlock(block)
       │
       ▼
[BlockchainEventBus]  ──► onBlockAdded(event)
  ├── BlockBroadcaster  ──► push block to all peers
  ├── Mempool           ──► remove confirmed transactions
  └── EventListener(s)  ──► application callbacks
```

### 4.3 Peer sync flow (node startup)

```
[BlockchainNode.start()]
       │
       ▼
[NodeServer.listen(port)]
       │
       ▼
[PeerManager.connect(seedPeers)]
  - TCP handshake
  - exchange nodeId + publicKey
  - AllowlistManager.check(nodeId) ──DENY──► disconnect
       │ ALLOW
       ▼
[SyncManager.syncChain()]
  - broadcast GET_STATUS to all peers
  - collect {nodeId, chainHeight} responses
  - find peer with max(chainHeight)
  if peer.chainHeight > local.chainHeight:
    ├── request blocks [local.height+1 .. peer.chainHeight]
    ├── validate each block via ConsensusEngine
    └── append to local chain via Blockchain.addBlock()
  else:
    └── local chain is up-to-date, proceed normally
```

### 4.4 New node onboarding flow (private chain access control)

```
[New Node]
       │
       │  sends JOIN_REQUEST(nodeId, publicKey)
       ▼
[Admin Node / InvitationService]
  - admin calls invitationService.generateToken(nodeId, expiryEpoch)
  - token = base64(nodeId + expiry + ECDSA_signature(adminPrivateKey))
  - sends token out-of-band to new node operator
       │
       ▼
[New Node]
  - includes token in JOIN_REQUEST
       │
       ▼
[AllowlistManager.verifyInvitation(token)]
  - parse token
  - verify ECDSA signature against admin public key
  - check expiry
  - if valid: add nodeId to allowlist
  - PermissionManager.assignRole(nodeId, NODE_OBSERVER)
       │
       ▼
[SyncManager] begins chain sync for new node
```

---

## 5. Class diagram

### 5.1 Core model

```
┌─────────────────────────────┐
│         <<record>>          │
│         BlockHeader         │
├─────────────────────────────┤
│ + version: int              │
│ + bits: int                 │
│ + nonce: long               │
│ + merkleRoot: String        │
│ + timestamp: Instant        │
└─────────────────────────────┘
             △ contained in
┌─────────────────────────────┐
│           Block             │
├─────────────────────────────┤
│ + index: int                │
│ + header: BlockHeader       │
│ + previousHash: String      │
│ + hash: String              │
│ + transactions: List<Tx>    │
├─────────────────────────────┤
│ + Block(index, prevHash,    │
│         txList, header)     │
│ + computeHash(): String     │
│ + toJson(): String          │
└─────────────────────────────┘
             ◇ (0..*)
┌─────────────────────────────┐
│       <<abstract>>          │
│        Transaction          │
├─────────────────────────────┤
│ + id: UUID                  │
│ + senderAddress: String     │
│ + receiverAddress: String   │
│ + amount: BigDecimal        │
│ + timestamp: Instant        │
│ + signature: byte[]         │
│ + metadata: Map<String,Obj> │
├─────────────────────────────┤
│ + sign(privateKey): void    │
│ + toSignableBytes(): byte[] │
└─────────────────────────────┘
             △ extended by developer
┌─────────────────────────────┐
│   AssetTransferTransaction  │  (example subclass)
├─────────────────────────────┤
│ + assetId: String           │
│ + assetType: AssetType      │
└─────────────────────────────┘
```

### 5.2 SPI interfaces and built-in implementations

```
┌──────────────────────────────────────────┐
│         <<interface>>                     │
│         ConsensusEngine                   │
├──────────────────────────────────────────┤
│ + validateBlock(block, chain): boolean    │
│ + prepareBlock(txs, prev): Block          │
│ + engineName(): String                    │
└──────┬─────────┬────────────┬────────────┘
       │         │            │
       ▼         ▼            ▼            user implements ──► CustomEngine
ProofOfWork  ProofOfAuth   RoundRobin
 Engine       Engine        Engine
       \         |            /
        \        |           /
         \       ▼          /
          PBFTEngine (also implements ConsensusEngine)


┌──────────────────────────────────────────┐
│         <<interface>>                     │
│        TransactionValidator               │
├──────────────────────────────────────────┤
│ + validate(tx, chain): ValidationResult   │
└──────┬──────────────────────────┬─────────┘
       ▼                          ▼
 SignatureValidator         BalanceValidator
       \                          /
        ──► CompositeValidator ◄──   (chains multiple)


┌──────────────────────────────────────────┐
│         <<interface>>                     │
│        BlockchainStorage                  │
├──────────────────────────────────────────┤
│ + saveBlock(block): void                  │
│ + loadBlock(index): Block                 │
│ + loadAll(): List<Block>                  │
│ + exists(hash): boolean                   │
│ + deleteAll(): void                       │
└──────┬─────────────┬──────────────────────┘
       ▼             ▼             user implements ──► CustomStorage
InMemoryStorage  LevelDBStorage
                    FileSystemStorage
```

### 5.3 Blockchain orchestration

```
┌──────────────────────────────────────────────────┐
│               BlockchainConfig                    │
├──────────────────────────────────────────────────┤
│ - consensusEngine: ConsensusEngine               │
│ - transactionValidators: List<TxValidator>       │
│ - storage: BlockchainStorage                     │
│ - eventBus: BlockchainEventBus                   │
│ - difficulty: int                                │
│ - blockTimeSeconds: int                          │
│ - port: int                                      │
├──────────────────────────────────────────────────┤
│ + builder(): Builder                             │
└────────────────────┬─────────────────────────────┘
                     │ creates
┌────────────────────▼─────────────────────────────┐
│               BlockchainNode                      │
├──────────────────────────────────────────────────┤
│ - blockchain: Blockchain                         │
│ - mempool: TransactionMempool                    │
│ - peerManager: PeerManager                       │
│ - nodeServer: NodeServer                         │
│ - syncManager: SyncManager                       │
│ - accessControl: AllowlistManager               │
├──────────────────────────────────────────────────┤
│ + start(): void                                  │
│ + stop(): void                                   │
│ + submitTransaction(tx): void                    │
│ + getChain(): Blockchain                         │
└──────────────────────────────────────────────────┘

┌────────────────────────────────────────────────┐
│             Blockchain                          │
├────────────────────────────────────────────────┤
│ - chain: List<Block>                           │
│ - consensusEngine: ConsensusEngine             │
│ - storage: BlockchainStorage                   │
│ - eventBus: BlockchainEventBus                 │
├────────────────────────────────────────────────┤
│ + addBlock(block): void                        │
│ + isChainValid(): boolean                      │
│ + getBlock(index): Block                       │
│ + getLatestBlock(): Block                      │
│ + size(): int                                  │
└────────────────────────────────────────────────┘
```

### 5.4 Wallet and crypto

```
┌──────────────────────────────────┐
│          ECKeyPair               │
├──────────────────────────────────┤
│ - publicKey: PublicKey           │
│ - privateKey: PrivateKey         │
├──────────────────────────────────┤
│ + getPublicKeyHex(): String      │
│ + getPrivateKeyHex(): String     │
│ + toString(): String  (masked)   │
└──────────────┬───────────────────┘
               │ used by
┌──────────────▼───────────────────┐
│            Wallet                │
├──────────────────────────────────┤
│ - keyPair: ECKeyPair             │
│ - address: String                │
├──────────────────────────────────┤
│ + getAddress(): String           │
│ + sign(tx): Transaction          │
│ + getBalance(chain): BigDecimal  │
└──────────────────────────────────┘

┌──────────────────────────────────┐
│         WalletManager            │
├──────────────────────────────────┤
│ - wallets: Map<String, Wallet>   │
│ - keystorePath: Path             │
├──────────────────────────────────┤
│ + createWallet(): Wallet         │
│ + importWallet(privKeyHex)       │
│ + getWallet(address): Wallet     │
│ + saveKeystore(passphrase)       │
│ + loadKeystore(passphrase)       │
└──────────────────────────────────┘

Utilities (all static methods):
  HashUtil            sha256(), sha3_256(), doubleHash()
  ECDSASignatureUtil  sign(), verify()
  KeyPairGenerator    generate()
  AddressUtil         deriveAddress(publicKey)
  MerkleTree          buildRoot(), getProof(), verifyProof()
```

### 5.5 Access control

```
┌──────────────────────────────────────┐
│          AllowlistManager            │
├──────────────────────────────────────┤
│ - allowedNodes: Set<String>          │
├──────────────────────────────────────┤
│ + isAllowed(nodeId): boolean         │
│ + add(nodeId): void                  │
│ + remove(nodeId): void               │
│ + verifyInvitation(token): boolean   │
└──────────────────────────────────────┘

┌──────────────────────────────────────┐
│          PermissionManager           │
├──────────────────────────────────────┤
│ - roleAssignments: Map<String, Role> │
├──────────────────────────────────────┤
│ + assignRole(nodeId, role): void     │
│ + hasRole(nodeId, role): boolean     │
│ + getRole(nodeId): NodeRole          │
└──────────────────────────────────────┘

┌──────────────────────────────────────┐
│         InvitationService            │
├──────────────────────────────────────┤
│ - adminKeyPair: ECKeyPair            │
├──────────────────────────────────────┤
│ + generateToken(nodeId, expiry)      │
│   : String (base64 signed token)     │
└──────────────────────────────────────┘

enum NodeRole { NODE_ADMIN, NODE_MINER, NODE_OBSERVER }
```

### 5.6 Event system

```
┌─────────────────────────────────────────────┐
│           BlockchainEventBus                 │
├─────────────────────────────────────────────┤
│ - listeners: CopyOnWriteArrayList<Listener> │
├─────────────────────────────────────────────┤
│ + register(listener): void                  │
│ + unregister(listener): void                │
│ + publish(event): void                      │
└─────────────────────────────────────────────┘

<<interface>> BlockchainEventListener
  + onEvent(BlockchainEvent): void

<<sealed>> BlockchainEvent
  ├── BlockAddedEvent        { block: Block }
  ├── TransactionSubmittedEvent { tx: Transaction }
  ├── PeerConnectedEvent     { peer: Peer }
  ├── PeerDisconnectedEvent  { peer: Peer }
  └── ForkDetectedEvent      { blockA: Block, blockB: Block }
```

---

## 6. Package layout

```
com.privatechain
├── core
│   ├── model
│   │   ├── Block.java
│   │   ├── BlockHeader.java
│   │   └── Transaction.java          ← abstract
│   ├── spi
│   │   ├── ConsensusEngine.java      ← interface
│   │   ├── TransactionValidator.java ← interface
│   │   └── BlockchainStorage.java    ← interface
│   ├── event
│   │   ├── BlockchainEvent.java      ← sealed
│   │   ├── BlockchainEventBus.java
│   │   ├── BlockchainEventListener.java
│   │   └── events/...
│   ├── exception
│   │   ├── BlockValidationException.java
│   │   ├── ConsensusException.java
│   │   └── TransactionValidationException.java
│   └── builder
│       ├── BlockchainConfig.java
│       ├── BlockchainNode.java
│       └── GenesisBlockFactory.java
├── crypto
│   ├── HashUtil.java
│   ├── ECDSASignatureUtil.java
│   ├── KeyPairGenerator.java
│   ├── AddressUtil.java
│   ├── MerkleTree.java
│   └── ECKeyPair.java
├── consensus
│   ├── pow
│   │   └── ProofOfWorkEngine.java
│   ├── poa
│   │   └── ProofOfAuthorityEngine.java
│   ├── pbft
│   │   └── PBFTEngine.java
│   └── roundrobin
│       └── RoundRobinEngine.java
├── storage
│   ├── memory
│   │   └── InMemoryStorage.java
│   ├── leveldb
│   │   └── LevelDBStorage.java
│   └── fs
│       └── FileSystemStorage.java
├── wallet
│   ├── Wallet.java
│   └── WalletManager.java
├── mempool
│   ├── TransactionMempool.java
│   ├── FeeBasedPrioritizer.java
│   └── TimestampPrioritizer.java
├── network
│   ├── peer
│   │   ├── Peer.java
│   │   └── PeerManager.java
│   ├── sync
│   │   └── SyncManager.java
│   ├── gossip
│   │   └── GossipProtocol.java
│   └── rpc
│       ├── NodeServer.java
│       └── BlockBroadcaster.java
└── access
    ├── AllowlistManager.java
    ├── PermissionManager.java
    ├── InvitationService.java
    └── NodeRole.java
```

---

## 7. Key design decisions

### 7.1 Why `blockchain-core` has zero runtime dependencies

`blockchain-core` defines all public contracts (model, SPI, events). Every Java project can safely depend on it without risking dependency conflicts. The Bouncy Castle, Netty, and LevelDB dependencies that teams often fight over are isolated in their respective modules. A project that only needs the data model and wants to provide its own crypto and storage can depend solely on `blockchain-core`.

### 7.2 Why `Transaction` is abstract rather than an interface

Interfaces cannot have fields, and `Transaction` has six required fields that every implementation must have. Using an abstract class means library code can safely read `tx.senderAddress` and `tx.signature` without casting. Developers subclass to add fields and have access to the protected `metadata` map for lightweight extension without subclassing.

### 7.3 Why the consensus engine is injected, not configured via a string

String-based configuration (`consensusEngine: "POW"`) requires a registry and reflection, both of which are fragile. Constructor injection means the type system guarantees a valid engine is always present. It also means custom engines (the primary use case for this library) work identically to built-in ones.

### 7.4 Thread safety strategy

`Blockchain.addBlock()` acquires a `ReentrantLock`. All reads of `chain` are done under a `ReadWriteLock`. `TransactionMempool` wraps its `PriorityQueue` with a `ReentrantLock`. `BlockchainEventBus` uses `CopyOnWriteArrayList` so listener registration never blocks event publication.

### 7.5 Serialization strategy for Transaction subtypes

Jackson `@JsonTypeInfo(use = Id.CLASS)` is used on `Transaction`. The full class name is written as a `_type` field. This means zero registration overhead but requires that subclass JAR files be present on the deserializing node's classpath. For cross-organization networks, `@JsonTypeInfo(use = Id.NAME)` with a shared type registry is recommended instead and is configurable via `BlockchainConfig.transactionTypeRegistry()`.