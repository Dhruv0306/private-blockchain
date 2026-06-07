package com.privatechain.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.privatechain.core.builder.BlockchainConfig;
import com.privatechain.core.builder.BlockchainNode;
import com.privatechain.core.exception.BlockValidationException;
import com.privatechain.core.exception.BlockchainException;
import com.privatechain.core.model.Block;
import com.privatechain.core.model.Transaction;
import com.privatechain.storage.memory.InMemoryStorage;
import org.junit.jupiter.api.*;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ChainExporter} — verifies JSON round-trips, CSV format,
 * and error handling (FR-SER-02, FR-SER-03, NFR-SEC-03).
 *
 * <p>Tests use {@link BlockchainConfig#builder()} with the default no-op consensus
 * engine and an {@link InMemoryStorage} backend. This keeps the test self-contained
 * within the {@code blockchain-storage} module's test classpath.</p>
 *
 * @since 1.0.0
 */
@DisplayName("ChainExporter")
class ChainExporterTest {

    /**
     * Node started fresh before each test; stopped in {@link #tearDown()}.
     */
    private BlockchainNode node;

    /**
     * Direct reference to the storage so tests can inspect it independently.
     */
    private InMemoryStorage storage;

    @BeforeEach
    void setUp() {
        storage = new InMemoryStorage();
        node = BlockchainConfig.builder()
            .storage(storage)
            .chainId("exporter-test-chain")
            .build();
        node.start();
    }

    @AfterEach
    void tearDown() {
        node.stop();
    }

    // ─── toJson ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("toJson(Blockchain)")
    class ToJson {

        @Test
        @DisplayName("returns a valid JSON array containing the genesis block")
        void genesisOnlyChainProducesJsonArray() throws Exception {
            String json = ChainExporter.toJson(node.getChain());

            assertNotNull(json);
            assertFalse(json.isBlank());

            // Parse back as raw list of blocks using the shared mapper
            List<Block> parsed = BlockSerializer.MAPPER.readValue(
                json, new TypeReference<>() {
                });

            assertEquals(1, parsed.size(), "genesis-only chain must produce a 1-element array");
            assertEquals(0, parsed.get(0).getIndex());
            assertEquals(Block.GENESIS_PREVIOUS_HASH, parsed.get(0).getPreviousHash());
        }

        @Test
        @DisplayName("multi-block chain serialises all blocks in index order")
        void multiBlockChainSerializesAllBlocks() throws Exception {
            // Mine 3 extra blocks on top of genesis using the default NoOp engine
            for (int i = 0; i < 3; i++) {
                Block mined = node.getChain().getConsensusEngine()
                    .mineBlock(List.of(), node.getChain().getLatestBlock());
                node.getChain().addBlock(mined);
            }

            String json = ChainExporter.toJson(node.getChain());
            List<Block> parsed = BlockSerializer.MAPPER.readValue(
                json, new TypeReference<>() {
                });

            assertEquals(4, parsed.size(), "should have genesis + 3 mined blocks");
            for (int i = 0; i < parsed.size(); i++) {
                assertEquals(i, parsed.get(i).getIndex(),
                    "block at position " + i + " must have index " + i);
            }
        }

        @Test
        @DisplayName("throws NullPointerException when chain is null")
        void nullChainThrowsNpe() {
            assertThrows(NullPointerException.class, () -> ChainExporter.toJson(null));
        }
    }

    // ─── fromJson ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("fromJson(String, BlockchainStorage)")
    class FromJson {

        @Test
        @DisplayName("restores the same number of blocks as the original chain")
        void restoredChainHeightMatchesOriginal() {
            // Mine 2 blocks beyond genesis
            for (int i = 0; i < 2; i++) {
                Block mined = node.getChain().getConsensusEngine()
                    .mineBlock(List.of(), node.getChain().getLatestBlock());
                node.getChain().addBlock(mined);
            }

            String json = ChainExporter.toJson(node.getChain());

            InMemoryStorage fresh = new InMemoryStorage();
            ChainExporter.fromJson(json, fresh);

            assertEquals(3, fresh.chainHeight(),
                "restored storage must contain genesis + 2 mined blocks");
        }

        @Test
        @DisplayName("every restored block passes hash validation (NFR-SEC-03)")
        void restoredBlocksPassHashValidation() {
            Block mined = node.getChain().getConsensusEngine()
                .mineBlock(List.of(), node.getChain().getLatestBlock());
            node.getChain().addBlock(mined);

            String json = ChainExporter.toJson(node.getChain());

            InMemoryStorage fresh = new InMemoryStorage();
            ChainExporter.fromJson(json, fresh);

            for (Block block : fresh.loadAll()) {
                assertTrue(block.isHashValid(),
                    "Block at index " + block.getIndex()
                        + " must pass hash validation after restore");
            }
        }

        @Test
        @DisplayName("restored blocks have identical hashes to the originals")
        void restoredBlockHashesMatchOriginal() {
            String json = ChainExporter.toJson(node.getChain());

            InMemoryStorage fresh = new InMemoryStorage();
            ChainExporter.fromJson(json, fresh);

            List<Block> original = storage.loadAll();
            List<Block> restored = fresh.loadAll();

            assertEquals(original.size(), restored.size());
            for (int i = 0; i < original.size(); i++) {
                assertEquals(original.get(i).getHash(), restored.get(i).getHash(),
                    "Hash mismatch at index " + i);
                assertEquals(original.get(i).getPreviousHash(), restored.get(i).getPreviousHash(),
                    "PreviousHash mismatch at index " + i);
            }
        }

        @Test
        @DisplayName("throws NullPointerException when json is null")
        void nullJsonThrowsNpe() {
            assertThrows(NullPointerException.class,
                () -> ChainExporter.fromJson(null, new InMemoryStorage()));
        }

        @Test
        @DisplayName("throws NullPointerException when storage is null")
        void nullStorageThrowsNpe() {
            assertThrows(NullPointerException.class,
                () -> ChainExporter.fromJson("[]", null));
        }

        @Test
        @DisplayName("throws IllegalArgumentException when json is blank")
        void blankJsonThrowsIae() {
            assertThrows(IllegalArgumentException.class,
                () -> ChainExporter.fromJson("   ", new InMemoryStorage()));
        }

        @Test
        @DisplayName("throws BlockchainException when JSON is syntactically invalid")
        void malformedJsonThrowsBlockchainException() {
            // "not-valid-json" cannot be parsed as a List<Block>, so Jackson throws
            // an IOException which ChainExporter wraps in BlockchainException.
            assertThrows(BlockchainException.class,
                () -> ChainExporter.fromJson("not-valid-json", new InMemoryStorage()),
                "Malformed JSON must raise BlockchainException wrapping the IOException");
        }

        @Test
        @DisplayName("throws BlockValidationException when a block has a tampered hash (NFR-SEC-03)")
        void tamperedBlockHashThrowsBlockValidationException() {
            // Export the genesis chain to get structurally valid JSON, then corrupt the
            // hash value so that Block#isHashValid() returns false for the first block.
            String validJson = ChainExporter.toJson(node.getChain());

            // Replace the very first hex character of the "hash" field with 'z'
            // (not a valid hex digit).  The JSON stays parseable but the hash no
            // longer matches the block's recomputed digest.
            String corruptJson = validJson.replaceFirst(
                "\"hash\":\"([0-9a-f])",
                "\"hash\":\"z");

            assertThrows(BlockValidationException.class,
                () -> ChainExporter.fromJson(corruptJson, new InMemoryStorage()),
                "A block whose hash field does not match its recomputed digest must "
                    + "cause BlockValidationException");
        }
    }

    // ─── toCsv ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("toCsv(Blockchain)")
    class ToCsv {

        @Test
        @DisplayName("genesis-only chain (no transactions) returns only the header row")
        void noTransactionsReturnsHeaderOnly() {
            String csv = ChainExporter.toCsv(node.getChain());

            assertNotNull(csv);
            // The header is always present; split by newline
            String[] lines = csv.split("\n");
            assertEquals(1, lines.length, "expected only the header row when no transactions exist");
            assertTrue(lines[0].startsWith("blockIndex,blockHash,txId"),
                "first line must be the CSV header");
        }

        @Test
        @DisplayName("block with two inline transactions produces three rows (header + 2)")
        void twoTransactionsProduceCorrectRowCount() {
            // Submit two transactions and mine a block containing them
            Transaction tx1 = makeTransaction("alice-addr", "bob-addr", "10.00");
            Transaction tx2 = makeTransaction("bob-addr", "carol-addr", "5.50");
            node.submitTransaction(tx1);
            node.submitTransaction(tx2);

            List<Transaction> selected = node.getMempool().getTopN(10);
            Block mined = node.getChain().getConsensusEngine()
                .mineBlock(selected, node.getChain().getLatestBlock());
            node.getChain().addBlock(mined);

            String csv = ChainExporter.toCsv(node.getChain());
            String[] lines = csv.split("\n");

            // header + 2 transaction rows
            assertEquals(3, lines.length, "expected header + 2 data rows");
        }

        @Test
        @DisplayName("CSV rows contain block hash and correct amounts")
        void csvRowsContainCorrectData() {
            Transaction tx = makeTransaction("alice-addr", "bob-addr", "42.00");
            node.submitTransaction(tx);

            Block mined = node.getChain().getConsensusEngine()
                .mineBlock(node.getMempool().getTopN(10), node.getChain().getLatestBlock());
            node.getChain().addBlock(mined);

            String csv = ChainExporter.toCsv(node.getChain());
            String[] lines = csv.split("\n");

            // line at index 1 is the first (and only) transaction row
            String dataRow = lines[1];
            assertTrue(dataRow.contains(mined.getHash()),
                "data row must contain the block hash");
            assertTrue(dataRow.contains("42.00"),
                "data row must contain the amount");
            assertTrue(dataRow.contains(tx.getId().toString()),
                "data row must contain the transaction UUID");
        }

        @Test
        @DisplayName("throws NullPointerException when chain is null")
        void nullChainThrowsNpe() {
            assertThrows(NullPointerException.class, () -> ChainExporter.toCsv(null));
        }
    }

    // ─── toCsv – csvEscape edge cases ────────────────────────────────────────
    //
    // The private csvEscape helper has four distinct code paths that are not
    // exercised by the happy-path CSV tests above:
    //
    //   1. value == null              → returns ""
    //   2. value contains ','         → wraps in double-quotes
    //   3. value contains '"'         → wraps + escapes inner quotes as ""
    //   4. value contains '\n'        → wraps in double-quotes
    //
    // Each test below mines a single block whose transaction carries a crafted
    // address that forces one of those paths.

    @Nested
    @DisplayName("toCsv – csvEscape edge cases")
    class ToCsvEscape {

        @Test
        @DisplayName("plain address without special characters is written as-is (no quoting)")
        void plainAddressIsNotQuoted() {
            // A regular address contains no comma, double-quote, or newline, so
            // csvEscape must return the value unchanged (the else-branch / fall-through).
            Transaction tx = makeTransaction("alice-addr", "bob-addr", "1.00");
            node.submitTransaction(tx);

            Block mined = node.getChain().getConsensusEngine()
                .mineBlock(node.getMempool().getTopN(10), node.getChain().getLatestBlock());
            node.getChain().addBlock(mined);

            String csv = ChainExporter.toCsv(node.getChain());
            String dataRow = csv.split("\n")[1];

            // The addresses must appear literally — not wrapped in extra double-quotes
            assertTrue(dataRow.contains("alice-addr"),
                "plain sender address must appear verbatim in the CSV row");
            assertTrue(dataRow.contains("bob-addr"),
                "plain receiver address must appear verbatim in the CSV row");
            assertFalse(dataRow.contains("\"alice-addr\""),
                "plain address must not be wrapped in double-quotes");
        }

        @Test
        @DisplayName("address containing a comma is wrapped in double-quotes (RFC-4180 §2.6)")
        void addressWithCommaIsQuoted() {
            // "alice,evil" contains a comma → csvEscape must wrap it in double-quotes.
            Transaction tx = makeTransaction("alice,evil", "bob-addr", "1.00");
            node.submitTransaction(tx);

            Block mined = node.getChain().getConsensusEngine()
                .mineBlock(node.getMempool().getTopN(10), node.getChain().getLatestBlock());
            node.getChain().addBlock(mined);

            String csv = ChainExporter.toCsv(node.getChain());
            assertTrue(csv.contains("\"alice,evil\""),
                "address containing a comma must be enclosed in double-quotes");
        }

        @Test
        @DisplayName("address containing a double-quote is escaped as \"\" inside quoted field (RFC-4180 §2.7)")
        void addressWithDoubleQuoteIsEscaped() {
            // "alice\"evil" contains a double-quote → csvEscape must wrap AND escape it.
            Transaction tx = makeTransaction("alice\"evil", "bob-addr", "1.00");
            node.submitTransaction(tx);

            Block mined = node.getChain().getConsensusEngine()
                .mineBlock(node.getMempool().getTopN(10), node.getChain().getLatestBlock());
            node.getChain().addBlock(mined);

            String csv = ChainExporter.toCsv(node.getChain());
            // Expected output: "alice""evil"  (outer quotes wrap, inner quote doubled)
            assertTrue(csv.contains("\"alice\"\"evil\""),
                "embedded double-quote must be escaped as \"\" inside a quoted field");
        }

        @Test
        @DisplayName("address containing a newline is wrapped in double-quotes (RFC-4180 §2.6)")
        void addressWithNewlineIsQuoted() {
            // "alice\Nevil" contains a newline → csvEscape must wrap it in double-quotes.
            Transaction tx = makeTransaction("alice\nevil", "bob-addr", "1.00");
            node.submitTransaction(tx);

            Block mined = node.getChain().getConsensusEngine()
                .mineBlock(node.getMempool().getTopN(10), node.getChain().getLatestBlock());
            node.getChain().addBlock(mined);

            String csv = ChainExporter.toCsv(node.getChain());
            assertTrue(csv.contains("\"alice\nevil\""),
                "address containing a newline must be enclosed in double-quotes");
        }
    }

    // ─── Utility-class constructor guard ──────────────────────────────────────

    @Nested
    @DisplayName("utility-class constructor")
    class UtilityConstructor {

        @Test
        @DisplayName("private constructor throws UnsupportedOperationException via reflection")
        void constructorThrowsUnsupportedOperationException() throws Exception {
            // Reach the private constructor via reflection to cover the defensive throw.
            var ctor = ChainExporter.class.getDeclaredConstructor();
            ctor.setAccessible(true);

            var ex = assertThrows(InvocationTargetException.class, ctor::newInstance,
                "Reflective instantiation must raise InvocationTargetException");
            assertInstanceOf(UnsupportedOperationException.class, ex.getCause(),
                "Root cause must be UnsupportedOperationException");
        }
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Creates a minimal concrete {@link Transaction} subclass for testing.
     *
     * <p>Defined as an anonymous inner class here to keep the test self-contained
     * without requiring {@code MoneyTransferTransaction} from the examples module
     * on the test classpath.</p>
     *
     * @param sender   sender address
     * @param receiver receiver address
     * @param amount   decimal amount string
     * @return an unsigned transaction ready for submission
     */
    private static Transaction makeTransaction(String sender, String receiver, String amount) {
        return new Transaction(
            UUID.randomUUID(),
            sender,
            receiver,
            new BigDecimal(amount),
            Instant.now(),
            Map.of()) {
            // Anonymous subclass — no extra fields needed for this test
        };
    }
}
