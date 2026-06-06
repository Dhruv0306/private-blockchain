# Milestone 10 — Examples, Documentation, and Release

## private-blockchain · Implementation Plan

**Version:** 1.0.0-SNAPSHOT → 1.0.0  
**Author:** Implementation plan generated from full codebase analysis  
**Prerequisites:** Milestones 0–9 complete and passing `mvn verify`  
**Estimated effort:** ~1 week  
**Last updated:** 2026-06-06

---

## Table of Contents

1. [Current State Assessment](#1-current-state-assessment)
2. [Pre-requisite Bug Fix](#2-pre-requisite-bug-fix)
3. [Phase 1 — Serialisation Gap and Examples](#3-phase-1--serialisation-gap-and-examples)
    - 3a. ChainExporter (FR-SER-02/03)
    - 3b. SimpleChainDemo + MoneyTransferTransaction (T-074)
    - 3c. SpringBootDemo (T-075)
    - 3d. VotingConsensusEngine + Demo (T-074)
4. [Phase 2 — Quality Gates](#4-phase-2--quality-gates)
    - 4a. Javadoc Audit (T-076)
    - 4b. Performance Test (T-078)
    - 4c. Security Audit Test (T-079)
5. [Phase 3 — Documentation](#5-phase-3--documentation)
    - 5a. README Quick-Start (T-077)
    - 5b. Javadoc Site to GitHub Pages (T-081)
6. [Phase 4 — Release](#6-phase-4--release)
    - 6a. Root POM Changes (T-080)
    - 6b. release.yml Changes (T-080)
    - 6c. CHANGELOG.md (T-080)
7. [File Delivery Order](#7-file-delivery-order)
8. [Build Verification Commands](#8-build-verification-commands)
9. [Acceptance Criteria Coverage](#9-acceptance-criteria-coverage)
10. [Known Post-1.0 Gaps](#10-known-post-10-gaps)

---

## 1. Current State Assessment

### What exists (M0–M9)

| Module                 | Status          | Key classes                                                                                                                                                                      |
|------------------------|-----------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `blockchain-core`      | ✅ Complete      | `Block`, `BlockHeader`, `Transaction`, `Blockchain`, `BlockchainNode`, `BlockchainConfig`, `GenesisBlockFactory`, `BlockchainEventBus`, `TransactionMempool`, all SPI interfaces |
| `blockchain-crypto`    | ✅ Complete      | `HashUtil`, `ECDSASignatureUtil`, `ECKeyPair`, `KeyPairGenerator`, `AddressUtil`, `MerkleTree`                                                                                   |
| `blockchain-consensus` | ✅ Complete      | `ProofOfWorkEngine`, `ProofOfAuthorityEngine`, `RoundRobinEngine`, `PBFTEngine`, `DifficultyAdjuster`, `ConsensusSupport`, TCK                                                   |
| `blockchain-storage`   | ✅ Complete      | `InMemoryStorage`, `LevelDBStorage`, `RocksDBStorage`, `FileSystemStorage`, `BlockSerializer`, `StorageContractTest`                                                             |
| `blockchain-network`   | ✅ Complete      | `PeerManager`, `NodeServer`, `NodeClient`, `MessageCodec`, `GossipProtocol`, `BlockBroadcaster`, `SyncManager`, `ForkResolver`                                                   |
| `blockchain-wallet`    | ✅ Complete      | `Wallet`, `WalletManager`                                                                                                                                                        |
| `blockchain-access`    | ✅ Complete      | `AllowlistManager`, `PermissionManager`, `InvitationService`, `NodeRole`                                                                                                         |
| `blockchain-spring`    | ✅ Complete      | `BlockchainAutoConfiguration`, `BlockchainProperties`, `BlockchainHealthIndicator`                                                                                               |
| `examples/*`           | ❌ **POMs only** | No Java source files exist                                                                                                                                                       |

### What is missing (M10 gaps)

| Gap                             | Required by          | Target module                   |
|---------------------------------|----------------------|---------------------------------|
| `ChainExporter.java`            | FR-SER-02, FR-SER-03 | `blockchain-storage`            |
| `SimpleChainDemo.java`          | T-074 [P0]           | `examples/simple-chain`         |
| `MoneyTransferTransaction.java` | T-074, AC-09         | `examples/simple-chain`         |
| `SpringChainApp.java`           | T-075 [P0]           | `examples/spring-boot-demo`     |
| `BlockchainRestController.java` | T-075 [P0]           | `examples/spring-boot-demo`     |
| `application.yml`               | T-075                | `examples/spring-boot-demo`     |
| `VotingConsensusEngine.java`    | T-074, AC-08         | `examples/custom-consensus`     |
| `VotingConsensusDemo.java`      | T-074                | `examples/custom-consensus`     |
| `package-info.java` files       | T-076                | `blockchain-core`               |
| `PerformanceTest.java`          | T-078                | `blockchain-core`               |
| `PrivateKeyLeakTest.java`       | T-079                | `blockchain-crypto`             |
| `ChainExporterTest.java`        | JaCoCo gate          | `blockchain-storage`            |
| `maven-release-plugin` config   | T-080                | root `pom.xml`                  |
| `distributionManagement`        | T-080                | root `pom.xml`                  |
| `CHANGELOG.md` 1.0.0 entry      | T-080                | root                            |
| README quick-start section      | T-077                | root                            |
| Javadoc site publish step       | T-081 [P2]           | `.github/workflows/javadoc.yml` |

### Known bug to fix first

`Blockchain.java` line: `import org.apache.logging.log4j.internal.annotation.SuppressFBWarnings;`  
Correct import: `import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;`  
This is an internal Log4j annotation that is not on the classpath and will cause
SpotBugs and Qodana failures during the release build.

---

## 2. Pre-requisite Bug Fix

**Fix this before any Phase 1 work touches `blockchain-core`.**

### File: `blockchain-core/src/main/java/com/privatechain/core/builder/Blockchain.java`

Remove:

```java
import org.apache.logging.log4j.internal.annotation.SuppressFBWarnings;
```

Replace with:

```java
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
```

The annotation is already on the classpath via the `spotbugs-annotations:4.8.3` dependency
declared in `blockchain-core/pom.xml` as `provided` scope. No POM changes needed.

After the fix, verify the module builds cleanly in isolation:

```bash
mvn clean install -pl blockchain-core -am
```

---

## 3. Phase 1 — Serialisation Gap and Examples

### 3a. ChainExporter — `blockchain-storage` (FR-SER-02, FR-SER-03)

**Why `blockchain-storage` and not `blockchain-core`?**  
`blockchain-core` declares Jackson as `optional=true` (zero mandatory transitive deps —
design.md §7.1). `blockchain-storage` already has Jackson as a mandatory, non-optional
dependency and owns the shared `BlockSerializer.MAPPER` instance. Placing
`ChainExporter` here keeps `blockchain-core` dependency-free and reuses the already-configured `ObjectMapper`.

---

#### 3a-1. New file: `blockchain-storage/src/main/java/com/privatechain/storage/ChainExporter.java`

**Package:** `com.privatechain.storage`  
**Visibility:** `public final`  
**Pattern:** Utility class — private constructor, all static methods.

**Fields:**

```java
// No instance fields. Reuse BlockSerializer.MAPPER (package-private static field).
```

**Method 1 — `toJson(Blockchain chain) : String`**

Serializes the entire chain to a JSON array string. Each element in the array is
the full JSON representation of one `Block`, using the same `BlockSerializer.MAPPER`
that all storage backends use. The resulting format is:

```json
[
  {
    "index": 0,
    "hash": "...",
    "previousHash": "...",
    "header": {
      ...
    },
    "transactions": []
  },
  {
    "index": 1,
    "hash": "...",
    ...
  }
]
```

Implementation steps:

1. Call `chain.getChain()` under the `Blockchain` read-lock (already thread-safe —
   `Blockchain.getChain()` delegates to `storage.loadAll()` under a `ReadWriteLock`).
2. Call `BlockSerializer.MAPPER.writeValueAsString(blocks)` where `blocks` is the
   `List<Block>` returned above.
3. Catch `JsonProcessingException` and rethrow as
   `com.privatechain.core.exception.BlockchainException` with a descriptive message.
4. Return the JSON string (never null).

**Method 2 — `fromJson(String json, BlockchainStorage storage) : void`**

Deserializes a JSON array produced by `toJson` back into the given storage backend,
effectively restoring a chain from a dump without network sync.

Implementation steps:

1. Validate that `json` is non-null and non-blank; validate `storage` is non-null.
2. Call `BlockSerializer.MAPPER.readValue(json, new TypeReference<List<Block>>(){})`.
3. For each `Block` in the resulting list (in order), call `BlockSerializer.verifyHash(block)`
   — this is `private` inside `BlockSerializer`. To avoid breaking encapsulation, call
   `BlockSerializer.fromJson(BlockSerializer.toJson(block))` which applies the
   existing hash-verification logic. Alternatively, call `block.isHashValid()` directly
   and throw `BlockValidationException` if false (same guard, no encapsulation needed).
4. Call `storage.saveBlock(block)` for each verified block.
5. Catch `JsonProcessingException` and rethrow as `BlockchainException`.

> **Design note:** This method does not accept a `Blockchain` instance (the chain
> manager) because it bypasses the consensus validation enforced by `Blockchain.addBlock()`.
> This is intentional — chain import is an administrative / bootstrap operation that should
> only restore a trusted dump. Normal network block acceptance must go through
> `Blockchain.addBlock()` which runs the `ConsensusEngine`.

**Method 3 — `toCsv(Blockchain chain) : String`**

Produces a flat CSV with one row per transaction across all blocks. Format:

```
blockIndex,blockHash,txId,senderAddress,receiverAddress,amount,timestamp,txType
0,0000...,3f4a...,addr1,addr2,10.00,2026-06-05T10:00:00Z,MoneyTransferTransaction
```

Implementation steps:

1. Build a `StringBuilder` and append the header row:
   `"blockIndex,blockHash,txId,senderAddress,receiverAddress,amount,timestamp,txType\n"`
2. Iterate `chain.getChain()` (preserves block order).
3. For each `Block`, iterate `block.getTransactions()`.
4. For each `Transaction`, append:
   ```
   {block.getIndex()},{block.getHash()},{tx.getId()},{tx.getSenderAddress()},
   {tx.getReceiverAddress()},{tx.getAmount().toPlainString()},
   {tx.getTimestamp()},{tx.getClass().getSimpleName()}
   ```
   Values containing commas must be quoted — check `senderAddress` in particular.
   Amounts from `BigDecimal.toPlainString()` will not contain commas. Address strings
   in this project are Base58Check or hex, so no commas. For safety, apply the quoting
   rule defensively to all String fields.
5. Return `sb.toString()`. Returns only the header if no transactions exist.
6. No external CSV library. `StringBuilder` is sufficient.

**Javadoc requirements (NFR-UX-01):**

All three methods need full Javadoc with `@param`, `@return`, and `@throws`. The class
Javadoc must reference FR-SER-02, FR-SER-03, and `BlockSerializer`.

**Checkstyle notes:**

- `HideUtilityClassConstructor` fires unless the constructor is explicitly `private`
  and throws `UnsupportedOperationException`.
- Line length: keep the CSV header string under 120 characters; split with `+` if needed.
- No star imports.

---

#### 3a-2. New file: `blockchain-storage/src/test/java/com/privatechain/storage/ChainExporterTest.java`

**Package:** `com.privatechain.storage`  
**Purpose:** Hit JaCoCo 80% coverage gate for `blockchain-storage` and verify FR-SER-02/03.

**Test structure (JUnit 5):**

```java

@BeforeEach
void setUp() {
    storage = new InMemoryStorage();
    engine = new ProofOfWorkEngine(1);  // difficulty=1: fast mining
    eventBus = new BlockchainEventBus();
    chain = new Blockchain(engine, storage, eventBus);
    // Start node to auto-create genesis block
    node = new BlockchainNode(BlockchainConfig.builder()
            .consensusEngine(engine)
            .storage(storage)
            .build());
    node.start();
}
```

> **Dependency note:** `ChainExporterTest` needs `blockchain-core`, `blockchain-consensus`,
> and `blockchain-storage` all on the test classpath. Check `blockchain-storage/pom.xml`:
> `blockchain-core` is already a compile dependency. Add `blockchain-consensus` as
> `test` scope if not present:
>
> ```xml
> <dependency>
>     <groupId>com.privatechain</groupId>
>     <artifactId>blockchain-consensus</artifactId>
>     <scope>test</scope>
> </dependency>
> ```

**Test cases:**

| Test name                                          | What it verifies                                                                                                   |
|----------------------------------------------------|--------------------------------------------------------------------------------------------------------------------|
| `toJson_emptyChainReturnsJsonArrayWithGenesisOnly` | Output is a valid JSON array with 1 element after `node.start()`                                                   |
| `toJson_multiBlockChainRoundTrips`                 | Mine 3 blocks; `toJson` → `fromJson` into a fresh `InMemoryStorage`; assert both storages contain identical blocks |
| `fromJson_restoredChainPassesHashValidation`       | Each block in the restored chain passes `block.isHashValid()`                                                      |
| `toCsv_noTransactionsReturnsHeaderOnly`            | Output contains exactly the header row when all blocks are empty                                                   |
| `toCsv_transactionsProducedCorrectRows`            | Mine a block with 2 mock transactions; verify CSV has 3 rows (header + 2)                                          |
| `toJson_nullChainThrowsNPE`                        | `ChainExporter.toJson(null)` throws `NullPointerException`                                                         |
| `fromJson_nullJsonThrowsNPE`                       | `ChainExporter.fromJson(null, storage)` throws `NullPointerException`                                              |
| `fromJson_nullStorageThrowsNPE`                    | `ChainExporter.fromJson("{}", null)` throws `NullPointerException`                                                 |

---

### 3b. SimpleChainDemo + MoneyTransferTransaction — `examples/simple-chain` (T-074)

The `examples/simple-chain/pom.xml` already exists and points the exec plugin to
`com.privatechain.examples.SimpleChainDemo`. Both Java source files live under:

```
examples/simple-chain/src/main/java/com/privatechain/examples/
```

---

#### 3b-1. New file: `MoneyTransferTransaction.java`

**Package:** `com.privatechain.examples`  
**Extends:** `com.privatechain.core.model.Transaction`  
**Purpose:** Demonstrate FR-TX-05 (custom subtypes), AC-09 (JSON round-trip), and the
`@JsonTypeInfo` polymorphism mechanism from design.md §7.5.

**Fields (private final):**

```java
private final String currency;   // ISO-4217 code, e.g. "USD"
private final String reference;  // optional free-text memo; may be null
```

**Constructor — annotated for Jackson `@JsonCreator`:**

```java

@JsonCreator
public MoneyTransferTransaction(
        @JsonProperty("id") UUID id,
        @JsonProperty("senderAddress") String senderAddress,
        @JsonProperty("receiverAddress") String receiverAddress,
        @JsonProperty("amount") BigDecimal amount,
        @JsonProperty("timestamp") Instant timestamp,
        @JsonProperty("metadata") Map<String, Object> metadata,
        @JsonProperty("currency") String currency,
        @JsonProperty("reference") String reference) {

    super(id, senderAddress, receiverAddress, amount, timestamp, metadata);
    this.currency = Objects.requireNonNull(currency, "currency must not be null");
    this.reference = reference; // nullable
}
```

**Factory method (for clean construction in demos):**

```java
public static MoneyTransferTransaction of(
        String senderAddress,
        String receiverAddress,
        BigDecimal amount,
        String currency) {

    return new MoneyTransferTransaction(
            UUID.randomUUID(),
            senderAddress,
            receiverAddress,
            amount,
            Instant.now(),
            null,
            currency,
            null);
}
```

**`toSignableBytes()` override:**

Append `|currency` to the parent canonical string to bind the currency field into
the signature. This prevents an attacker from changing `"USD"` to `"BTC"` post-signing.

```java

@Override
public byte[] toSignableBytes() {
    byte[] base = super.toSignableBytes();
    byte[] curr = ("|" + currency).getBytes(StandardCharsets.UTF_8);
    byte[] combined = new byte[base.length + curr.length];
    System.arraycopy(base, 0, combined, 0, base.length);
    System.arraycopy(curr, 0, combined, base.length, curr.length);
    return combined;
}
```

**Getters:**

```java
public String getCurrency() {
    return currency;
}

public String getReference() {
    return reference;
}
```

**`toString()` override:**

Include `currency` and `reference` fields in addition to the parent summary. Never
include signature bytes. Format example:

```
MoneyTransferTransaction{id=..., sender=addr1, receiver=addr2, amount=50.00,
  currency=USD, reference=invoice-001, timestamp=..., signed=true}
```

**Checkstyle note:** The `VisibilityModifier` rule permits `protected` fields (set in
`checkstyle.xml`). All fields here are `private final` — no issues.

---

#### 3b-2. New file: `SimpleChainDemo.java`

**Package:** `com.privatechain.examples`  
**Purpose:** End-to-end runnable demo satisfying T-074, NFR-UX-02 (≤10 lines for
core setup), AC-09 (custom transaction JSON round-trip), and FR-SER-02/03 (ChainExporter).

**Class structure:**

```java
public final class SimpleChainDemo {
    public static void main(String[] args) throws Exception { ...}

    private static void section(String title) { ...}  // print separator
}
```

**`main` execution sequence (in order):**

**Step 1 — Build and start the node (≤10 lines):**

```java
BlockchainNode node = BlockchainConfig.builder()
        .consensusEngine(new ProofOfWorkEngine(2))   // difficulty=2: fast but real PoW
        .storage(new InMemoryStorage())
        .build();
node.

start();
```

Demonstrates FR-CFG-01, FR-CFG-02, NFR-UX-02.

**Step 2 — Create a wallet:**

```java
WalletManager wm = new WalletManager();
Wallet alice = wm.createWallet();
Wallet bob = wm.createWallet();
System.out.

println("Alice: "+alice.getAddress());
        System.out.

println("Bob:   "+bob.getAddress());
```

The `WalletManager` constructor requires no arguments; it generates a new in-memory
key store. `createWallet()` calls `KeyPairGenerator.generateECKeyPair()` internally
and wraps the result in a `Wallet`.

**Step 3 — Create, sign, and submit three transactions:**

```java
for(int i = 1;
i <=3;i++){
MoneyTransferTransaction tx = MoneyTransferTransaction.of(
        alice.getAddress(), bob.getAddress(),
        BigDecimal.valueOf(10 * i), "USD");
    alice.

sign(tx);   // attaches ECDSA signature
    node.

submitTransaction(tx);
    System.out.

printf("Submitted tx %d: %s%n",i, tx.getId());
        }
        System.out.

println("Mempool size: "+node.getMempool().

size());
```

`node.submitTransaction(tx)` runs the configured validator chain then calls
`mempool.submit(tx)` which publishes `TransactionSubmittedEvent`. With no custom
validators configured, only the null-check guard fires — the three transactions
enter the pool immediately.

**Step 4 — Mine a block:**

```java
List<Transaction> selected = node.getMempool().getTopN(10);
Block candidate = node.getChain().getConsensusEngine()
        .mineBlock(selected, node.getChain().getLatestBlock());
node.

getChain().

addBlock(candidate);
System.out.

println("Block #"+candidate.getIndex() +" mined: "+candidate.

getHash());
```

**Important:** `mineBlock` signature is `mineBlock(List<Transaction>, Block previous)`.
The call `node.getChain().getConsensusEngine()` is available as a `public` method on
`Blockchain`. `node.getChain()` returns the live `Blockchain` reference.

When `Blockchain.addBlock(candidate)` succeeds, it: persists the block via
`BlockchainStorage.saveBlock()`, publishes `BlockAddedEvent` on the event bus, which
triggers the mempool listener to remove the three confirmed transactions.

**Step 5 — Validate the chain:**

```java
boolean valid = node.getChain().isChainValid();
System.out.

println("Chain valid: "+valid);
assert valid :"Chain integrity check failed!";
```

`isChainValid()` checks hash linkage and tamper detection for every block pair.

**Step 6 — Print node status:**

```java
BlockchainNode.NodeStatus status = node.status();
System.out.

printf("Height: %d | Mempool: %d | Engine: %s%n",
       status.chainHeight(),status.

mempoolSize(),status.

consensusEngine());
```

`NodeStatus` is a `record` with accessors `chainHeight()`, `mempoolSize()`,
`peerCount()`, `lastBlockTime()`, and `consensusEngine()`.

**Step 7 — Export to JSON and verify round-trip (FR-SER-02):**

```java
String json = ChainExporter.toJson(node.getChain());
System.out.

println("Chain JSON (first 200 chars): "+json.substring(0, 200) +"...");

// Restore into a fresh storage and verify block count matches
InMemoryStorage freshStorage = new InMemoryStorage();
ChainExporter.

fromJson(json, freshStorage);
assert freshStorage.

chainHeight() ==node.

getChain().

size()
    :"Round-trip chain height mismatch";
            System.out.

println("JSON round-trip: OK ("+freshStorage.chainHeight() +" blocks)");
```

**Step 8 — Export to CSV (FR-SER-03):**

```java
String csv = ChainExporter.toCsv(node.getChain());
System.out.

println("CSV output:\n"+csv);
```

**Step 9 — Wallet balance query:**

```java
BigDecimal bobBalance = bob.getBalance(node.getChain());
System.out.

println("Bob balance: "+bobBalance +" USD");
// Expected: 60.00 (10 + 20 + 30)
```

**Step 10 — Stop the node:**

```java
node.stop();
System.out.

println("Node stopped cleanly.");
```

**Checkstyle notes for this class:**

- Line length: the `BlockchainConfig.builder()` chain fits in one statement.
  Break across multiple lines if the chain exceeds 120 chars.
- `TodoComment` warning: do not include any `TODO:` markers.
- `HideUtilityClassConstructor`: add a private no-arg constructor if there are
  only static methods. Since `main` is `static`, Java allows a class without an
  explicit constructor — Checkstyle will flag this. Add `private SimpleChainDemo() {}`.
- The `assert` keyword requires the JVM flag `-ea`. In the exec plugin invocation
  (`mvn exec:java`) this is already standard. Alternatively, replace with explicit
  `if (!valid) throw new AssertionError(...)` to avoid the flag.
- `checkstyle-suppressions.xml` already suppresses `JavadocMethod` and `JavadocType`
  for `src/test/` but not for `examples/`. Add a suppression for `examples/` to
  allow the demo methods to skip Javadoc:

  ```xml
  <suppress
      files="[/\\]examples[/\\]"
      checks="JavadocMethod|JavadocType|MissingJavadocMethod"/>
  ```

  The line-length suppression is already in `checkstyle-suppressions.xml`:
  ```xml
  <suppress files="[/\\]examples[/\\]" checks="LineLength"/>
  ```

---

### 3c. SpringBootDemo — `examples/spring-boot-demo` (T-075)

The existing POM already has `blockchain-spring`, `spring-boot-starter-web`, and
`spring-boot-starter-actuator`. All three source files go under:

```
examples/spring-boot-demo/src/main/java/com/privatechain/examples/spring/
examples/spring-boot-demo/src/main/resources/
```

---

#### 3c-1. New file: `SpringChainApp.java`

**Package:** `com.privatechain.examples.spring`

```java

@SpringBootApplication
public class SpringChainApp {
    public static void main(String[] args) {
        SpringApplication.run(SpringChainApp.class, args);
    }
}
```

This is the only class needed for the Spring entry point. `BlockchainAutoConfiguration`
activates automatically; `BlockchainNode` is created, started, and managed by Spring.

**One additional bean to define here — a demo wallet:**

```java

@Bean
public Wallet demoWallet() throws Exception {
    WalletManager wm = new WalletManager();
    return wm.createWallet();
}
```

This creates a fresh `Wallet` on every application start. The controller uses it to
sign demo transactions. In production, this would load from an encrypted keystore.

---

#### 3c-2. New file: `BlockchainRestController.java`

**Package:** `com.privatechain.examples.spring`

**Class signature:**

```java

@RestController
@RequestMapping("/api")
public class BlockchainRestController {

    private final BlockchainNode node;
    private final Wallet demoWallet;

    public BlockchainRestController(BlockchainNode node, Wallet demoWallet) {
        this.node = Objects.requireNonNull(node);
        this.demoWallet = Objects.requireNonNull(demoWallet);
    }
    ...
}
```

**Endpoint 1 — `GET /api/status`:**

```java

@GetMapping("/status")
public ResponseEntity<BlockchainNode.NodeStatus> getStatus() {
    return ResponseEntity.ok(node.status());
}
```

Returns HTTP 200 with JSON body. The `NodeStatus` record is a Java 17 record
so Jackson serializes it via component accessors automatically. Sample response:

```json
{
  "chainHeight": 3,
  "mempoolSize": 0,
  "peerCount": 0,
  "lastBlockTime": "2026-06-06T10:00:00Z",
  "consensusEngine": "ProofOfWork"
}
```

**Endpoint 2 — `GET /api/chain`:**

```java

@GetMapping("/chain")
public ResponseEntity<List<Block>> getChain() {
    return ResponseEntity.ok(node.getChain().getChain());
}
```

Returns HTTP 200 with the full list of blocks as a JSON array. The `Block` class is
annotated with `@JsonProperty` on its `@JsonCreator` constructor parameters, so
Jackson knows which fields to serialize. Ensure `Jackson2ObjectMapperBuilder` in the
Spring autoconfiguration registers `JavaTimeModule` so `Instant` fields in
`BlockHeader` serialise as ISO-8601 strings — add this bean to `SpringChainApp`:

```java

@Bean
public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
    return builder -> builder
            .modules(new JavaTimeModule())
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
}
```

This is the same config `BlockSerializer.MAPPER` uses; applying it to Spring's mapper
keeps the REST output format consistent with storage output.

**Endpoint 3 — `GET /api/chain/{index}`:**

```java

@GetMapping("/chain/{index}")
public ResponseEntity<Block> getBlock(@PathVariable int index) {
    try {
        return ResponseEntity.ok(node.getChain().getBlock(index));
    } catch (NoSuchElementException e) {
        return ResponseEntity.notFound().build();
    }
}
```

`Blockchain.getBlock(int index)` throws `NoSuchElementException` when the index is
out of range. Map it to HTTP 404 as shown.

**Endpoint 4 — `POST /api/transactions`:**

```java
record TransactionRequest(
        String senderAddress,
        String receiverAddress,
        BigDecimal amount,
        String currency) {
}

@PostMapping("/transactions")
public ResponseEntity<Map<String, String>> submitTransaction(
        @RequestBody TransactionRequest req) {

    MoneyTransferTransaction tx = MoneyTransferTransaction.of(
            req.senderAddress(), req.receiverAddress(), req.amount(), req.currency());
    demoWallet.sign(tx);

    try {
        node.submitTransaction(tx);
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(Map.of("txId", tx.getId().toString(), "status", "PENDING"));
    } catch (TransactionValidationException e) {
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("error", e.getMessage()));
    }
}
```

**Endpoint 5 — `POST /api/mine`:**

Allows the demo user to trigger block mining manually. This avoids needing a
background mining thread for the demo:

```java

@PostMapping("/mine")
public ResponseEntity<Map<String, Object>> mineBlock() {
    List<Transaction> txs = node.getMempool().getTopN(100);
    if (txs.isEmpty()) {
        return ResponseEntity.ok(Map.of(
                "message", "Mempool is empty — no block mined",
                "mempoolSize", 0));
    }
    Block mined = node.getChain().getConsensusEngine()
            .mineBlock(txs, node.getChain().getLatestBlock());
    node.getChain().addBlock(mined);
    return ResponseEntity.ok(Map.of(
            "blockIndex", mined.getIndex(),
            "blockHash", mined.getHash(),
            "txCount", mined.getTransactions().size()));
}
```

**Error handling note:** Wrap the mining and addBlock calls in a try/catch for
`ConsensusException` and `BlockValidationException` (both extend `BlockchainException`).
Return HTTP 500 with the exception message if either throws.

---

#### 3c-3. New file: `examples/spring-boot-demo/src/main/resources/application.yml`

```yaml
spring:
  application:
    name: blockchain-demo

# Blockchain autoconfiguration
blockchain:
  enabled: true
  chain-id: spring-demo-chain
  difficulty: 2
  network-port: 8545
  block-time-seconds: 5
  max-peers: 10
  mempool:
    ttl: PT30M      # ISO-8601 duration: 30 minutes
    max-size: 1000

# Spring Boot management / Actuator
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: always

logging:
  level:
    com.privatechain: DEBUG
    root: INFO
```

**Property name notes:**  
`BlockchainProperties` uses `@ConfigurationProperties(prefix = "blockchain")`.
Check the exact property names bound in `BlockchainProperties.java` and match them
here. Common mismatches: `network-port` vs `networkPort` (Spring relaxed binding
handles `kebab-case` → `camelCase`), `mempool.ttl` vs `mempool.ttl` (nested `Mempool`
inner class already present in `BlockchainProperties`).

---

### 3d. VotingConsensusEngine + Demo — `examples/custom-consensus` (T-074)

The POM points the exec plugin to `com.privatechain.examples.VotingConsensusDemo`.
Source files:

```
examples/custom-consensus/src/main/java/com/privatechain/examples/
```

---

#### 3d-1. New file: `VotingConsensusEngine.java`

**Package:** `com.privatechain.examples`  
**Implements:** `com.privatechain.core.spi.ConsensusEngine`  
**Purpose:** Demonstrates FR-CONS-06 (custom engine injectable via config) and AC-08
(custom engine called for every `addBlock()`).

**Design:** A majority-vote engine where each registered validator must explicitly
`castVote(blockHash, validatorAddress)` before a block can be accepted. A block
is valid when `voteCount > floor(validatorCount / 2)` (strict majority).

**Fields:**

```java
// Immutable set of registered validator addresses (e.g. "validator-1", "validator-2")
private final List<String> validators;

// Concurrent map: blockHash → Set<validatorAddress that voted FOR this block>
private final ConcurrentHashMap<String, Set<String>> votes = new ConcurrentHashMap<>();
```

**Constructor:**

```java
public VotingConsensusEngine(Collection<String> validators) {
    if (validators == null || validators.isEmpty()) {
        throw new IllegalArgumentException("validators must not be null or empty");
    }
    // Defensive copy; sorted for deterministic quorum calculation
    this.validators = List.copyOf(validators.stream().sorted().toList());
}
```

**Method — `castVote(String blockHash, String validatorAddress) : void`:**

```java
public void castVote(String blockHash, String validatorAddress) {
    Objects.requireNonNull(blockHash, "blockHash must not be null");
    Objects.requireNonNull(validatorAddress, "validatorAddress must not be null");
    if (!validators.contains(validatorAddress)) {
        throw new IllegalArgumentException(
                "Unknown validator: " + validatorAddress
                        + ". Registered: " + validators);
    }
    votes.computeIfAbsent(blockHash,
            k -> ConcurrentHashMap.newKeySet()).add(validatorAddress);
}
```

**Method — `validateBlock(Block block, Blockchain chain) : boolean`:**

```java

@Override
public boolean validateBlock(Block block, Blockchain chain) {
    // Genesis block is always valid
    if (block.getIndex() == 0) {
        return true;
    }
    // Count votes for this block's hash
    Set<String> blockVotes = votes.getOrDefault(block.getHash(), Set.of());
    int quorum = validators.size() / 2 + 1;   // strict majority
    return blockVotes.size() >= quorum;
}
```

**Method — `mineBlock(List<Transaction> transactions, Block previousBlock) : Block`:**

Block production uses `ConsensusSupport.buildBlock(...)`.  
**Dependency note:** `ConsensusSupport` is in `blockchain-consensus`.
Check `examples/custom-consensus/pom.xml`: it already declares `blockchain-consensus`
as a dependency? The POM currently lists `blockchain-core`, `blockchain-crypto`, and
`blockchain-storage`. Add `blockchain-consensus` as a compile dependency:

```xml

<dependency>
    <groupId>com.privatechain</groupId>
    <artifactId>blockchain-consensus</artifactId>
</dependency>
```

Implementation:

```java

@Override
public Block mineBlock(List<Transaction> transactions, Block previousBlock) {
    Objects.requireNonNull(transactions, "transactions must not be null");
    Objects.requireNonNull(previousBlock, "previousBlock must not be null");

    Block candidate = ConsensusSupport.buildBlock(
            previousBlock,
            transactions,
            0,                      // bits: no PoW target
            0L,                     // nonce: not used by this engine
            null,                   // no miner address for this demo engine
            Instant.now());

    // Auto-cast votes from all registered validators for the demo
    // (In production, votes would arrive asynchronously over the network)
    for (String validator : validators) {
        castVote(candidate.getHash(), validator);
    }

    return candidate;
}
```

**Method — `engineName() : String`:**

```java

@Override
public String engineName() {
    return "VotingConsensus";
}
```

**Getters for inspection in tests:**

```java
public List<String> getValidators() {
    return validators;
}

public int getVoteCount(String blockHash) {
    return votes.getOrDefault(blockHash, Set.of()).size();
}
```

---

#### 3d-2. New file: `VotingConsensusDemo.java`

**Package:** `com.privatechain.examples`

**`main` execution sequence:**

**Step 1 — Set up three validators and create the engine:**

```java
List<String> validators = List.of("validator-A", "validator-B", "validator-C");
VotingConsensusEngine engine = new VotingConsensusEngine(validators);
```

**Step 2 — Build node with the custom engine:**

```java
BlockchainNode node = BlockchainConfig.builder()
        .consensusEngine(engine)         // FR-CONS-06: inject custom engine
        .storage(new InMemoryStorage())
        .build();
node.

start();
```

**Step 3 — Submit a transaction and mine a block:**

```java
// Create a simple placeholder transaction (no crypto needed for this demo)
MoneyTransferTransaction tx = MoneyTransferTransaction.of(
                "sender-addr", "receiver-addr", BigDecimal.TEN, "EUR");
node.

submitTransaction(tx);   // enters mempool without signature validation here

List<Transaction> pending = node.getMempool().getTopN(10);
// mineBlock also auto-casts votes from all three validators internally
Block mined = engine.mineBlock(pending, node.getChain().getLatestBlock());
```

**Step 4 — Show vote count before and after quorum:**

```java
System.out.printf("Votes for block %s: %d/%d (quorum: 2)%n",
                  mined.getHash().

substring(0,8),
    engine.

getVoteCount(mined.getHash()),
        validators.

size());
```

**Step 5 — Add block to chain (triggers `validateBlock`):**

```java
node.getChain().

addBlock(mined);
System.out.

println("Block added: "+mined.getIndex());
        System.out.

println("Chain height: "+node.status().

chainHeight());
        System.out.

println("Chain valid: "+node.getChain().

isChainValid());
```

**Step 6 — Demonstrate rejection when quorum is not met:**

Create a second block candidate but only cast one vote, then call `addBlock` and
catch the expected `BlockValidationException`:

```java
VotingConsensusEngine strictEngine =
        new VotingConsensusEngine(List.of("v1", "v2", "v3", "v4", "v5"));

Block anotherCandidate = ConsensusSupport.buildBlock(
        mined, List.of(), 0, 0L, null, Instant.now());
strictEngine.

castVote(anotherCandidate.getHash(), "v1");   // only 1 of 5

// Manually validate to show quorum failure
boolean valid = strictEngine.validateBlock(anotherCandidate, node.getChain());
System.out.

println("Block accepted with 1/5 votes: "+valid);  // false
```

**Step 7 — Stop:**

```java
node.stop();
System.out.

println("VotingConsensusDemo complete.");
```

---

## 4. Phase 2 — Quality Gates

### 4a. Javadoc Audit — `blockchain-core` (T-076)

**Goal:** Zero `-Xdoclint:all` warnings when running `mvn javadoc:javadoc -pl blockchain-core`.

**Audit procedure:**

Run:

```bash
mvn javadoc:javadoc -pl blockchain-core -Dadditionalparam="-Xdoclint:all"
```

Inspect `blockchain-core/target/site/apidocs/` for warnings in the Maven output.

**Known gaps to address:**

**1. Missing `package-info.java` files (most common `-Xdoclint` failure).**

Create a `package-info.java` in each of these packages under
`blockchain-core/src/main/java/com/privatechain/core/`:

| Package          | File                | One-sentence description                                                               |
|------------------|---------------------|----------------------------------------------------------------------------------------|
| `core`           | `package-info.java` | Top-level package containing the core blockchain domain model and SPI interfaces.      |
| `core.model`     | `package-info.java` | Immutable domain objects: Block, BlockHeader, and the abstract Transaction base class. |
| `core.spi`       | `package-info.java` | Service Provider Interfaces that consumers implement to extend the library.            |
| `core.event`     | `package-info.java` | Sealed event hierarchy and the asynchronous publish-subscribe event bus.               |
| `core.exception` | `package-info.java` | Unchecked exception hierarchy rooted at BlockchainException.                           |
| `core.builder`   | `package-info.java` | Assembly layer — BlockchainConfig, BlockchainNode, and Blockchain live here.           |
| `core.mempool`   | `package-info.java` | In-memory transaction pool with pluggable prioritisation and TTL eviction.             |
| `core.network`   | `package-info.java` | Lifecycle interfaces for network subsystems injected by blockchain-network.            |

Each file follows the format:

```java
/**
 * {Description sentence from table above}
 *
 * @since 1.0.0
 */
package com.privatechain.core.model;
```

**2. `NodeStatus` record in `BlockchainNode.java`.**

The `NodeStatus` record already has a class-level Javadoc comment that documents
each component via `@param`. Verify that all five `@param` tags match the actual
component names (`chainHeight`, `mempoolSize`, `peerCount`, `lastBlockTime`,
`consensusEngine`). Records in Java 17 do not generate separate getter Javadoc —
the class-level `@param` tags serve as the canonical documentation.

**3. `ConsensusEngine` SPI reference in `BlockchainConfig.Builder` methods.**

Run `-Xdoclint` and inspect whether any `{@link}` in `blockchain-core` references
classes in `blockchain-consensus`. Cross-module `{@link}` tags that resolve only at
aggregate Javadoc time cause `JavadocReference` violations when building per-module.
Remove any such references from `blockchain-core` Javadoc (they were explicitly
flagged in previous milestones).

**4. `ValidationResult` inner enum.**

`ValidationResult` has an inner enum `ValidationStatus`. Verify each enum constant
(`VALID`, `INVALID_SIGNATURE`, `INSUFFICIENT_FUNDS`, `DUPLICATE`, `CUSTOM_REJECTION`)
has a Javadoc comment. Enum constants without Javadoc trigger `MissingJavadocMethod`
from Checkstyle.

**maven-javadoc-plugin configuration (add to root `pom.xml` `<reporting>`):**

```xml

<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-javadoc-plugin</artifactId>
    <version>3.7.0</version>
    <reportSets>
        <reportSet>
            <id>aggregate</id>
            <inherited>false</inherited>
            <reports>
                <report>aggregate</report>
            </reports>
        </reportSet>
        <reportSet>
            <id>default</id>
            <reports>
                <report>javadoc</report>
            </reports>
        </reportSet>
    </reportSets>
    <configuration>
        <doclint>none</doclint>     <!-- aggregate report only: no build failure -->
        <quiet>true</quiet>
        <detectJavaApiLink>false</detectJavaApiLink>
        <additionalJOption>-Xdoclint:all</additionalJOption>  <!-- per-module: strict -->
    </configuration>
</plugin>
```

**Checkstyle note:** Checkstyle's `JavadocType` check requires the `@since` tag on
all public types. Verify all `package-info.java` files include `@since 1.0.0`.

---

### 4b. Performance Test — `blockchain-core` (T-078)

**File:** `blockchain-core/src/test/java/com/privatechain/core/PerformanceTest.java`  
**Package:** `com.privatechain.core`  
**Framework:** JUnit 5 with `@Tag("performance")`

**Dependency note:** This test needs `ProofOfWorkEngine` from `blockchain-consensus`
and `InMemoryStorage` from `blockchain-storage`. Both must be `test` scope:

```xml
<!-- In blockchain-core/pom.xml test section -->
<dependency>
    <groupId>com.privatechain</groupId>
    <artifactId>blockchain-consensus</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
<groupId>com.privatechain</groupId>
<artifactId>blockchain-storage</artifactId>
<scope>test</scope>
</dependency>
```

Check if these are already present (they may be needed for existing integration tests
in `blockchain-core`). If not, add them.

**Test strategy — two separate measurements:**

**Test 1 — `chainValidationOf10000BlocksUnder2Seconds` (primary T-078 test):**

```java

@Test
@Tag("performance")
void chainValidationOf10000BlocksUnder2Seconds() {
    // Setup: mine 10,000 blocks with difficulty=0 (no PoW target — pure speed)
    InMemoryStorage storage = new InMemoryStorage();
    ProofOfWorkEngine engine = new ProofOfWorkEngine(0);  // no difficulty
    BlockchainEventBus bus = new BlockchainEventBus();
    Blockchain chain = new Blockchain(engine, storage, bus);

    // Genesis
    Block genesis = GenesisBlockFactory.create("perf-test");
    chain.addBlock(genesis);

    // Build 9,999 more blocks (difficulty=0 means mining is instant hash computation)
    for (int i = 1; i < 10_000; i++) {
        Block b = engine.mineBlock(List.of(), chain.getLatestBlock());
        chain.addBlock(b);
    }

    // Time only the validation pass
    long start = System.nanoTime();
    boolean valid = chain.isChainValid();
    long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

    assertTrue(valid, "Chain must be valid");
    assertTrue(elapsedMs < 2_000L,
            "isChainValid() on 10,000 blocks took " + elapsedMs + "ms; target is <2000ms");

    System.out.printf("isChainValid(10,000 blocks): %dms%n", elapsedMs);
    bus.shutdown();
}
```

**Important:** `ProofOfWorkEngine(0)` may not be a valid difficulty — the constructor
throws `IllegalArgumentException` if `difficulty < 1`. Use `ProofOfWorkEngine(1)` and
reduce expectations, or change the strategy: use `RoundRobinEngine` (difficulty=0 N/A)
which mines instantly:

```java
RoundRobinEngine engine = new RoundRobinEngine(
        List.of("node-1"), "node-1");
```

This is cleaner. `RoundRobinEngine.mineBlock()` returns immediately without a hash
search loop. Also add `blockchain-consensus` as a test dep.

**Test 2 — `sha256ThroughputMeets50kPerSecond` (NFR-PERF-04):**

```java

@Test
@Tag("performance")
void sha256ThroughputMeets50kPerSecond() {
    // 50,000 hashes in under 1 second
    int iterations = 50_000;
    long start = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
        HashUtil.sha256("benchmark-input-" + i);
    }
    long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
    assertTrue(elapsedMs < 1_000L,
            "SHA-256 throughput below target. " + iterations + " hashes took " + elapsedMs + "ms");
    System.out.printf("SHA-256 throughput: %d hashes/sec%n",
            (long) iterations * 1_000L / Math.max(1, elapsedMs));
}
```

**CI exclusion strategy:**  
Add `@Tag("performance")` to all tests in this class. In `pom.xml` surefire config,
performance tests can be excluded from the default profile if they are too slow for CI:

```xml

<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <excludedGroups>${skipPerformanceTests}</excludedGroups>
    </configuration>
</plugin>
```

With `<skipPerformanceTests/>` defaulting to empty (runs all tests), or set via
`-DskipPerformanceTests=performance` to exclude on slow runners.

**SpotBugs note:** `PerformanceTest` is in `src/test/` which is already excluded from
SpotBugs analysis via the existing plugin config.

---

### 4c. Security Audit Test — `blockchain-crypto` (T-079, NFR-SEC-01)

**File:** `blockchain-crypto/src/test/java/com/privatechain/crypto/PrivateKeyLeakTest.java`  
**Package:** `com.privatechain.crypto`  
**Purpose:** Automated regression guard for NFR-SEC-01: private keys MUST NEVER
appear in logs, `toString()`, or unencrypted serialization.

**Test 1 — `ecKeyPairToStringDoesNotContainPrivateKey`:**

```java

@Test
void ecKeyPairToStringDoesNotContainPrivateKey() {
    ECKeyPair keyPair = KeyPairGenerator.generateECKeyPair();
    String privateHex = keyPair.getPrivateKeyHex();
    String str = keyPair.toString();

    assertFalse(str.contains(privateHex),
            "ECKeyPair.toString() must not expose the private key hex");
    // Verify the public key IS present (toString is still informative)
    assertTrue(str.contains(keyPair.getPublicKeyHex()),
            "ECKeyPair.toString() should include the public key");
}
```

**Test 2 — `walletToStringDoesNotContainPrivateKey`:**

```java

@Test
void walletToStringDoesNotContainPrivateKey() {
    // Wallet is in blockchain-wallet which depends on blockchain-crypto.
    // If blockchain-wallet is not on the test classpath, test Wallet
    // indirectly by checking ECKeyPair masking is applied correctly.
    ECKeyPair keyPair = KeyPairGenerator.generateECKeyPair();
    String privateHex = keyPair.getPrivateKeyHex();

    // ECKeyPair.toString() delegates to the masked implementation
    String repr = keyPair.toString();
    assertFalse(repr.contains(privateHex),
            "Key representation must mask the private key");
    // The masked value must be a placeholder like "***" or similar
    assertTrue(repr.contains("***") || repr.contains("[MASKED]") || repr.contains("redacted"),
            "Key repr must contain a masking indicator: " + repr);
}
```

**Test 3 — `signingOperationDoesNotLogPrivateKey` (JUL log capture):**

```java

@Test
void signingOperationDoesNotLogPrivateKey() throws Exception {
    ECKeyPair keyPair = KeyPairGenerator.generateECKeyPair();
    String privateHex = keyPair.getPrivateKeyHex();

    // Install a capturing handler on the root JUL logger
    Logger rootLogger = Logger.getLogger("");
    List<String> capturedMessages = Collections.synchronizedList(new ArrayList<>());
    Handler capturingHandler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            if (record.getMessage() != null) {
                capturedMessages.add(record.getMessage());
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };
    capturingHandler.setLevel(Level.ALL);
    rootLogger.addHandler(capturingHandler);

    try {
        // Perform a signing operation — this exercises ECDSASignatureUtil.sign()
        byte[] data = "test-data".getBytes(StandardCharsets.UTF_8);
        byte[] signature = ECDSASignatureUtil.sign(data, keyPair);
        // Also verify the signature (exercises the full code path)
        boolean verified = ECDSASignatureUtil.verify(data, signature, keyPair);
        assertTrue(verified);

        // Inspect all captured log messages
        for (String msg : capturedMessages) {
            assertFalse(msg.contains(privateHex),
                    "Log message contains private key hex: " + msg.substring(0, Math.min(100, msg.length())));
        }
    } finally {
        rootLogger.removeHandler(capturingHandler);
    }
}
```

**Test 4 — `ecKeyPairJacksonSerializationDoesNotIncludePrivateKey`:**

If Jackson is on the test classpath (it is for `blockchain-crypto` — check the POM;
if not, add it as `test` scope), verify that serializing an `ECKeyPair` via Jackson
does not expose the private key. Note: `ECKeyPair` is a `record` and its fields are
`private final`. Jackson can only serialize via `record` components or annotated
getters/methods. This test confirms the annotation design is correct:

```java

@Test
void ecKeyPairSerializationShouldNotExposePrivateKey() throws Exception {
    ECKeyPair keyPair = KeyPairGenerator.generateECKeyPair();
    String privateHex = keyPair.getPrivateKeyHex();

    ObjectMapper mapper = new ObjectMapper();
    String json = mapper.writeValueAsString(keyPair);

    // Either the private key is not serialised, or the field is missing entirely
    assertFalse(json.contains(privateHex),
            "ECKeyPair JSON serialisation must not contain the private key hex: " + json);
}
```

**SpotBugs note:** The test class is in `src/test/` — SpotBugs excludes test classes
by default in the existing configuration. No `@SuppressFBWarnings` needed.

---

## 5. Phase 3 — Documentation

### 5a. README Quick-Start Rewrite (T-077)

**Current README state:** Has a full module structure reference section and a dependency
summary table but lacks a functional quick-start that a new user can copy-paste in
5 minutes.

**Add immediately after the project title/description:**

---

```markdown
## Quick start — 5 minutes to your first private chain

### 1. Add the dependency

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

### 2. Start a node (≤10 lines)

```java
BlockchainNode node = BlockchainConfig.builder()
        .consensusEngine(new ProofOfWorkEngine(4))
        .storage(new InMemoryStorage())
        .build();
node.

start();
System.out.

println(node.status().

chainHeight()); // 1 (genesis block)
        node.

stop();
```

### 3. Define a custom transaction type

```java
public class PaymentTx extends Transaction {
    private final String currency;

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

    public String getCurrency() {
        return currency;
    }
}
```

The `@JsonTypeInfo` annotation on `Transaction` ensures `PaymentTx` survives a full
JSON round-trip automatically (AC-09).

### 4. Swap the consensus engine

```java
// Proof of Authority: only these addresses may produce blocks
Set<String> authorizedMiners = Set.of(aliceAddress, bobAddress);
BlockchainNode node = BlockchainConfig.builder()
        .consensusEngine(new ProofOfAuthorityEngine(authorizedMiners))
        .storage(new LevelDBStorage("/data/my-chain"))
        .build();
node.

start();
```

### 5. Spring Boot integration

Add `blockchain-spring` and configure via `application.yml`:

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
  chain-id: my-chain
```

`BlockchainNode` is created and started as a Spring bean automatically.
Override defaults by declaring your own `ConsensusEngine` or `BlockchainStorage` bean.

```

---

**Also add a "Module dependency table" section with exact GAV coordinates** matching
the production `distributionManagement` target:

```markdown
## Module coordinates

All modules share version `1.0.0` and group ID `com.privatechain`.

| Artifact ID             | Required for                              |
|-------------------------|-------------------------------------------|
| `blockchain-core`       | All usage — data model and SPI contracts  |
| `blockchain-crypto`     | ECDSA signing, Merkle trees, address util |
| `blockchain-consensus`  | PoW, PoA, PBFT, Round-Robin engines       |
| `blockchain-storage`    | LevelDB, RocksDB, FileSystem, InMemory    |
| `blockchain-wallet`     | Key management and transaction signing    |
| `blockchain-access`     | RBAC, allowlist, invitation tokens        |
| `blockchain-network`    | Netty P2P networking layer                |
| `blockchain-spring`     | Spring Boot 4.x autoconfiguration         |
```

---

### 5b. Javadoc Site to GitHub Pages (T-081, P2)

**New file: `.github/workflows/javadoc.yml`**

```yaml
name: Javadoc Site

on:
  push:
    branches: [ main ]
  workflow_dispatch:

permissions:
  contents: write

jobs:
  publish-javadoc:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven

      - name: Build project (skip tests for speed)
        run: mvn -B install -DskipTests

      - name: Generate aggregate Javadoc
        run: mvn -B javadoc:aggregate -pl . -am

      - name: Deploy to GitHub Pages
        uses: peaceiris/actions-gh-pages@v4
        with:
          github_token: ${{ secrets.GITHUB_TOKEN }}
          publish_dir: ./target/site/apidocs
          destination_dir: apidocs
          commit_message: 'docs: update Javadoc site [skip ci]'
```

**Root POM `<reporting>` section** (add if not already present):

```xml

<reporting>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-javadoc-plugin</artifactId>
            <version>3.7.0</version>
            <inherited>false</inherited>
            <reportSets>
                <reportSet>
                    <id>aggregate</id>
                    <inherited>false</inherited>
                    <reports>
                        <report>aggregate</report>
                    </reports>
                </reportSet>
            </reportSets>
            <configuration>
                <doclint>none</doclint>
                <quiet>true</quiet>
                <detectJavaApiLink>false</detectJavaApiLink>
                <excludePackageNames>
                    com.privatechain.examples
                </excludePackageNames>
            </configuration>
        </plugin>
    </plugins>
</reporting>
```

The `peaceiris/actions-gh-pages` action requires the repo to have GitHub Pages enabled
(Settings → Pages → Source: Deploy from branch → `gh-pages`).

---

## 6. Phase 4 — Release

### 6a. Root POM Changes (T-080)

**1. Add `<distributionManagement>`:**

```xml

<distributionManagement>
    <repository>
        <id>github</id>
        <name>GitHub Packages — private-blockchain</name>
        <url>https://maven.pkg.github.com/dhruv0306/private-blockchain</url>
    </repository>
    <snapshotRepository>
        <id>github-snapshots</id>
        <name>GitHub Packages — private-blockchain (snapshots)</name>
        <url>https://maven.pkg.github.com/dhruv0306/private-blockchain</url>
    </snapshotRepository>
</distributionManagement>
```

The `id` value (`github`) must match the `<server>` id in the runner's `settings.xml`.
The `release.yml` workflow uses `GITHUB_TOKEN` via the `setup-java` cache mechanism —
this is handled automatically by `actions/setup-java@v4` when `cache: maven` is set.

**2. Add `maven-release-plugin` to `<pluginManagement>`:**

```xml

<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-release-plugin</artifactId>
    <version>3.1.1</version>
    <configuration>
        <tagNameFormat>v@{project.version}</tagNameFormat>
        <autoVersionSubmodules>true</autoVersionSubmodules>
        <releaseVersion>1.0.0</releaseVersion>
        <developmentVersion>1.0.1-SNAPSHOT</developmentVersion>
        <scmCommentPrefix>[release]</scmCommentPrefix>
        <pushChanges>true</pushChanges>
        <localCheckout>true</localCheckout>
        <preparationGoals>clean verify</preparationGoals>
        <completionGoals>clean install</completionGoals>
    </configuration>
</plugin>
```

**3. Add a `release` profile for source and Javadoc JARs:**

Maven Central (and best practice for GitHub Packages) requires three artifacts per
module: the main JAR, a `-sources.jar`, and a `-javadoc.jar`. Add a profile that
activates on release:

```xml

<profiles>
    <profile>
        <id>release</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-source-plugin</artifactId>
                    <version>3.3.1</version>
                    <executions>
                        <execution>
                            <id>attach-sources</id>
                            <goals>
                                <goal>jar-no-fork</goal>
                            </goals>
                        </execution>
                    </executions>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-javadoc-plugin</artifactId>
                    <version>3.7.0</version>
                    <executions>
                        <execution>
                            <id>attach-javadocs</id>
                            <goals>
                                <goal>jar</goal>
                            </goals>
                            <configuration>
                                <doclint>none</doclint>
                                <quiet>true</quiet>
                            </configuration>
                        </execution>
                    </executions>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

**4. Verify `<scm>` section exists** (required by `maven-release-plugin`):

```xml

<scm>
    <connection>scm:git:https://github.com/dhruv0306/private-blockchain.git</connection>
    <developerConnection>scm:git:https://github.com/dhruv0306/private-blockchain.git</developerConnection>
    <url>https://github.com/dhruv0306/private-blockchain</url>
    <tag>HEAD</tag>
</scm>
```

The `<tag>HEAD</tag>` is replaced by `maven-release-plugin` with the actual version
tag (e.g. `v1.0.0`) during `release:prepare`.

---

### 6b. `release.yml` Changes (T-080)

Replace the current workflow body (which just runs `mvn deploy`) with the full
release plugin flow:

```yaml
name: Release

on:
  push:
    tags:
      - 'v*'

jobs:
  release:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4
        with:
          # Fetch all history so maven-release-plugin can push the release commit
          fetch-depth: 0

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven
          # Configures settings.xml with the GitHub token for GitHub Packages auth
          server-id: github
          server-username: GITHUB_ACTOR
          server-password: GITHUB_TOKEN

      - name: Configure Git identity
        run: |
          git config user.email "release-bot@privatechain.io"
          git config user.name "Release Bot"

      - name: Release prepare
        run: mvn -B release:prepare -Prelease
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          GITHUB_ACTOR: ${{ github.actor }}

      - name: Release perform
        run: mvn -B release:perform -Prelease
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          GITHUB_ACTOR: ${{ github.actor }}
```

**Trigger note:** The `release:prepare` step will create and push a `v1.0.0` tag.
To avoid an infinite loop (the tag push re-triggers this workflow), ensure the job's
`GITHUB_TOKEN` lacks write permission to trigger further workflows, or use `[skip ci]`
in the release commit message (already set via `scmCommentPrefix`).

**Alternative approach (simpler):** Since the repo already has a `release.yml` that
triggers on `v*` tags, the manual release flow is:

```bash
# Locally, before tagging:
mvn versions:set -DnewVersion=1.0.0
git add -A && git commit -m "[release] set version 1.0.0"
git tag v1.0.0
git push origin main --tags
# CI picks up the tag and runs 'mvn -B deploy -Prelease'
```

This avoids `release:prepare/perform` complexity. If this simpler approach is
preferred, update the existing `release.yml` to add `-Prelease`:

```yaml
- name: Publish to GitHub Packages
  run: mvn -B deploy -Prelease
  env:
    GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

---

### 6c. CHANGELOG.md — 1.0.0 Entry (T-080)

Add the following to `CHANGELOG.md` above any existing entries:

```markdown
## [1.0.0] — 2026-06-06

### Added

**Milestone 1 — Core data model**

- Immutable `Block` with `minerAddress` field for identity-based consensus
- Abstract `Transaction` base class with Jackson `@JsonTypeInfo` polymorphism
- `Blockchain` chain manager with `ReadWriteLock` concurrency control
- `BlockchainConfig` fluent builder and `BlockchainNode` lifecycle entry point
- `GenesisBlockFactory` producing a deterministic genesis block (64-zero `previousHash`)

**Milestone 2 — Cryptography**

- `HashUtil`: SHA-256, SHA3-256, double-SHA-256 returning hex strings
- `ECDSASignatureUtil`: secp256k1 ECDSA sign/verify via Bouncy Castle 1.84
- `KeyPairGenerator`, `ECKeyPair` (private key masked in `toString`)
- `AddressUtil`: SHA-256 → RIPEMD-160 → Base58Check address derivation
- `MerkleTree`: root computation and inclusion proof generation

**Milestone 3 — Storage**

- `InMemoryStorage`, `LevelDBStorage`, `RocksDBStorage`, `FileSystemStorage`
- `BlockSerializer`: shared Jackson `ObjectMapper` with hash-integrity verification
- `StorageContractTest` TCK run against all four implementations
- **New in M10:** `ChainExporter` — `toJson`, `fromJson`, `toCsv` (FR-SER-02/03)

**Milestone 4 — Consensus engines**

- `ProofOfWorkEngine`: SHA-256 mining with configurable leading-zero-bit difficulty
- `ProofOfAuthorityEngine`: authorized signer allowlist
- `RoundRobinEngine`: deterministic slot-based rotation for dev/test
- `PBFTEngine`: two-phase Byzantine fault-tolerant commit with `ViewChangeManager`
- `DifficultyAdjuster`: sliding-window auto-recalibration for PoW
- `ConsensusEngineContractTest` TCK run against all four engines

**Milestone 5 — Wallet and mempool**

- `Wallet`: secp256k1 key pair, address derivation, `sign(Transaction)`, `getBalance`
- `WalletManager`: create, import, and export wallets (AES-256-GCM keystore)
- `TransactionMempool`: `PriorityQueue` with TTL eviction, duplicate rejection,
  event-bus wiring for confirmed-transaction cleanup
- `FeeBasedPrioritizer`, `TimestampBasedPrioritizer`

**Milestone 6 — Access control**

- `PermissionManager`: RBAC with `NODE_ADMIN`, `NODE_MINER`, `NODE_OBSERVER`
- `AllowlistManager`: enforced before any inbound message processing
- `InvitationService`: ECDSA-signed time-limited invitation tokens

**Milestone 7 — Networking**

- `NodeServer`/`NodeClient`: Netty 4.2.13 TCP with NDJSON message codec
- `PeerManager`: heartbeat, reconnect, and graceful pruning
- `GossipProtocol`: fan-out to `ceil(log2(n))` random peers
- `BlockBroadcaster`: push new blocks to all connected peers on `BlockAddedEvent`
- `SyncManager`: startup chain sync from the peer with the highest chain height
- `ForkResolver`: canonical chain selection by cumulative difficulty

**Milestone 8 — Event bus and observability**

- `BlockchainEventBus`: `CopyOnWriteArrayList` backed, daemon executor, shutdown guard
- All five event types: `BlockAddedEvent`, `TransactionSubmittedEvent`,
  `PeerConnectedEvent`, `PeerDisconnectedEvent`, `ForkDetectedEvent`
- `BlockchainNode.status()` returning live `NodeStatus` record
- `PeerManagerLifecycle`, `NodeServerLifecycle`, `ChainSyncer` interfaces in `blockchain-core`
  preserving the zero-mandatory-dependency contract

**Milestone 9 — Spring Boot autoconfiguration**

- `BlockchainAutoConfiguration` with `@ConditionalOnMissingBean` override points
- `BlockchainProperties`: full `application.yml` binding with nested `Mempool` section
- `BlockchainHealthIndicator`: Actuator-compatible status without mandatory Actuator dep

**Milestone 10 — Examples, docs, and release**

- `SimpleChainDemo` with `MoneyTransferTransaction` (AC-09 JSON round-trip proof)
- `SpringChainApp` with REST endpoints for chain browsing and transaction submission
- `VotingConsensusEngine` custom engine example (FR-CONS-06, AC-08)
- `PerformanceTest`: `isChainValid()` on 10,000 blocks verified under 2 seconds
- `PrivateKeyLeakTest`: regression guard for NFR-SEC-01 log and `toString` safety
- 100% Javadoc coverage on all public APIs in `blockchain-core`
- `package-info.java` added to all eight packages in `blockchain-core`

### Security

- All CVE findings from Mend.io dependency scan addressed (see `owasp-suppressions.xml`
  for suppressed findings with justification)
- Bouncy Castle upgraded to 1.84 (CVE-2026-0636 resolved)
- Netty upgraded to 4.2.13.Final (five CVEs resolved)
- Spring Boot upgraded to 4.0.6 (CVE-2026-22733 addressed via managed suppression)

### Known limitations (post-1.0 backlog)

- No gRPC transport (see `docs/decisions/ADR-001-transport.md` and backlog T-B02)
- No Micrometer / `blockchain-metrics` module (backlog T-B03)
- No chain snapshot / checkpoint support (backlog T-B04)
- `ForkResolver` selects by cumulative difficulty only (no finality gadget)
- `PBFTEngine` quorum operates in-process only; distributed PBFT requires the
  network layer (T-B06)
```

---

## 7. File Delivery Order

Files must be delivered in this sequence to satisfy Maven's build order and avoid
missing-class compilation failures.

```
PRE-REQUISITE
  1. blockchain-core/…/builder/Blockchain.java          [bug fix: wrong SuppressFBWarnings import]

PHASE 1 — Serialisation + examples
  2. blockchain-storage/…/ChainExporter.java
  3. blockchain-storage/…/ChainExporterTest.java
  4. examples/custom-consensus/pom.xml                  [add blockchain-consensus dependency]
  5. examples/simple-chain/…/MoneyTransferTransaction.java
  6. examples/simple-chain/…/SimpleChainDemo.java
  7. examples/custom-consensus/…/VotingConsensusEngine.java
  8. examples/custom-consensus/…/VotingConsensusDemo.java
  9. examples/spring-boot-demo/…/SpringChainApp.java
 10. examples/spring-boot-demo/…/BlockchainRestController.java
 11. examples/spring-boot-demo/src/main/resources/application.yml
 12. checkstyle-suppressions.xml                        [add examples Javadoc suppression]

PHASE 2 — Quality gates
 13. blockchain-core/pom.xml                            [add blockchain-consensus + blockchain-storage test deps]
 14. blockchain-core/…/core/model/package-info.java     (and 7 other package-info.java files)
 15. blockchain-core/…/PerformanceTest.java
 16. blockchain-crypto/…/PrivateKeyLeakTest.java

PHASE 3 — Documentation
 17. README.md                                          [add quick-start section]
 18. pom.xml                                            [add maven-javadoc-plugin to <reporting>]
 19. .github/workflows/javadoc.yml

PHASE 4 — Release
 20. pom.xml                                            [add distributionManagement, scm, release profile, maven-release-plugin]
 21. .github/workflows/release.yml                      [update to use release profile]
 22. CHANGELOG.md
```

---

## 8. Build Verification Commands

Run these in order after completing each phase to catch issues early:

```bash
# Pre-requisite: verify bug fix
mvn clean install -pl blockchain-core -am

# Phase 1a: ChainExporter
mvn clean verify -pl blockchain-storage

# Phase 1b: SimpleChainDemo (compile only — exec needs installed deps)
mvn clean install -DskipTests -pl blockchain-core,blockchain-crypto,blockchain-consensus,blockchain-storage,blockchain-wallet
mvn clean package -pl examples/simple-chain
mvn exec:java -pl examples/simple-chain

# Phase 1c: SpringBootDemo (needs spring in classpath)
mvn clean package -pl examples/spring-boot-demo
# Run: mvn spring-boot:run -pl examples/spring-boot-demo

# Phase 1d: VotingConsensusDemo
mvn clean package -pl examples/custom-consensus
mvn exec:java -pl examples/custom-consensus

# Phase 2: Quality gates
mvn clean verify                                          # full build with all tests
mvn javadoc:javadoc -pl blockchain-core -Dadditionalparam="-Xdoclint:all"
mvn javadoc:aggregate -pl .                               # aggregate report

# Full build with SpotBugs and Checkstyle
mvn clean verify -Pspotbugs,checkstyle                   # or however profiles are named in the root POM

# Phase 4: Release (dry run)
mvn release:prepare -DdryRun=true -Prelease
```

---

## 9. Acceptance Criteria Coverage

| ID    | Area      | Criterion                                                               | Satisfied by                                                                | Status    |
|-------|-----------|-------------------------------------------------------------------------|-----------------------------------------------------------------------------|-----------|
| AC-01 | Core      | Same inputs → same block hash across JVM restarts                       | `Block.computeHash()` is deterministic (SHA-256, fixed input format)        | ✅ M1      |
| AC-02 | PoA       | 3-node cluster accepts only authorized-signer blocks                    | `ProofOfAuthorityEngineTest` (M4)                                           | ✅ M4      |
| AC-03 | PoW       | Difficulty=4 → hashes beginning with "0000"                             | `ProofOfWorkEngineTest` (M4)                                                | ✅ M4      |
| AC-04 | Storage   | LevelDB chain survives JVM kill and reloads identically                 | `LevelDBStorageTest` (M3)                                                   | ✅ M3      |
| AC-05 | Crypto    | Tampered signature rejected by `SignatureTransactionValidator`          | `SignatureUtilTest` (M2)                                                    | ✅ M2      |
| AC-06 | Network   | Joining node syncs to correct chain height within 5 seconds             | `TwoNodeIntegrationTest` (M7)                                               | ✅ M7      |
| AC-07 | Access    | Non-allowlisted peer block silently dropped and logged                  | `AllowlistManagerTest` (M6)                                                 | ✅ M6      |
| AC-08 | SPI       | Custom `ConsensusEngine` called for every `addBlock()`                  | `VotingConsensusDemo` — engine registered via `BlockchainConfig.builder()`  | ✅ **M10** |
| AC-09 | Extension | Custom `Transaction` subclass survives full JSON round-trip             | `MoneyTransferTransaction` serialised and deserialised in `SimpleChainDemo` | ✅ **M10** |
| AC-10 | Spring    | `blockchain-spring` auto-creates `BlockchainNode` bean with zero config | `BlockchainAutoConfigurationTest` (M9)                                      | ✅ M9      |

---

## 10. Known Post-1.0 Gaps

These items are explicitly out of scope for 1.0.0 and are tracked in the backlog.
They are documented here to prevent unplanned scope creep during M10 implementation.

| Backlog ID | Description                                     | Reason deferred                                                                     |
|------------|-------------------------------------------------|-------------------------------------------------------------------------------------|
| T-B01      | `RocksDBStorage` full production hardening      | Implementation exists but further benchmarking deferred                             |
| T-B02      | gRPC transport layer                            | ADR-001 recommends gRPC; implementation effort is a full milestone                  |
| T-B03      | `blockchain-metrics` Micrometer module          | Nice-to-have; Actuator bridge in `blockchain-spring` is sufficient for 1.0          |
| T-B04      | Chain snapshot / checkpoint support             | Required for fast bootstrap of new nodes on long chains                             |
| T-B05      | WebSocket block-explorer API                    | Developer tooling; not core infrastructure                                          |
| T-B06      | Docker Compose 3-node demo network              | Operational demo; depends on stable networking layer                                |
| —          | `FR-NET-06`: Protocol Buffers P2P framing       | Current implementation uses NDJSON over TCP (MessageCodec); proto3 framing deferred |
| —          | `FR-WALLET-03`: Web3 Secret Storage v3 keystore | Current keystore uses AES-256-GCM; Web3 format deferred to post-1.0                 |

---

*End of Milestone 10 Implementation Plan*
