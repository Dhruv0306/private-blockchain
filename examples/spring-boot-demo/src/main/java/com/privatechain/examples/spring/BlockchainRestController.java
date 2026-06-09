package com.privatechain.examples.spring;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.privatechain.core.builder.BlockchainNode;
import com.privatechain.core.exception.BlockValidationException;
import com.privatechain.core.exception.ConsensusException;
import com.privatechain.core.exception.TransactionValidationException;
import com.privatechain.core.model.Block;
import com.privatechain.core.model.Transaction;
import com.privatechain.wallet.Wallet;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * REST controller exposing the blockchain node as a set of HTTP endpoints.
 *
 * <p>Demonstrates the {@code blockchain-spring} autoconfiguration pattern (AC-10):
 * the {@link BlockchainNode} is injected as a Spring bean with no boilerplate — it
 * is created and started by {@code BlockchainAutoConfiguration} purely from
 * {@code application.yml} properties.</p>
 *
 * <h2>Endpoints</h2>
 * <table border="1">
 *   <caption>Available REST endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Description</th><th>Status</th></tr>
 *   <tr><td>GET</td> <td>/api/status</td>            <td>Node status snapshot</td>                 <td>200</td></tr>
 *   <tr><td>GET</td> <td>/api/chain</td>             <td>All blocks as a JSON array</td>           <td>200</td></tr>
 *   <tr><td>GET</td> <td>/api/chain/{index}</td>     <td>Single block by index</td>                <td>200 / 404</td></tr>
 *   <tr><td>POST</td><td>/api/transactions</td>      <td>Submit and sign a payment</td>            <td>202 / 422</td></tr>
 *   <tr><td>POST</td><td>/api/mine</td>              <td>Mine a block from the mempool</td>        <td>200 / 204</td></tr>
 * </table>
 *
 * <h2>Thread safety</h2>
 * <p>All fields are {@code final} and injected via constructor. {@link BlockchainNode}
 * is thread-safe internally (design.md §7.4). Spring MVC's default servlet thread pool
 * may invoke these endpoints concurrently without additional synchronization.</p>
 *
 * @see SpringChainApp
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api")
public class BlockchainRestController {

    // ─── Injected beans ───────────────────────────────────────────────────────

    /**
     * Autoconfigured node — provides chain, mempool, and status access.
     */
    private final BlockchainNode node;

    /**
     * Demo wallet used to sign transactions submitted via {@link #submitTransaction}.
     * Injected from the bean defined in {@link SpringChainApp#demoWallet()}.
     */
    private final Wallet demoWallet;

    // ─── Constructor injection ────────────────────────────────────────────────

    /**
     * Constructs the controller via Spring constructor injection.
     *
     * @param node       the autoconfigured blockchain node (non-null)
     * @param demoWallet the demo signing wallet (non-null)
     * @throws NullPointerException if either argument is null
     */
    public BlockchainRestController(BlockchainNode node, Wallet demoWallet) {
        this.node = Objects.requireNonNull(node, "node must not be null");
        this.demoWallet = Objects.requireNonNull(demoWallet, "demoWallet must not be null");
    }

    // ─── Endpoints ────────────────────────────────────────────────────────────

    /**
     * Returns a live snapshot of the node's operational status.
     *
     * <p>Sample response:</p>
     * <pre>{@code
     * {
     *   "chainHeight": 3,
     *   "mempoolSize": 0,
     *   "peerCount": 0,
     *   "lastBlockTime": "2026-06-06T10:00:00Z",
     *   "consensusEngine": "NoOp"
     * }
     * }</pre>
     *
     * @return HTTP 200 with the {@link BlockchainNode.NodeStatus} record as JSON
     */
    @GetMapping("/status")
    public ResponseEntity<BlockchainNode.NodeStatus> getStatus() {
        return ResponseEntity.ok(node.status());
    }

    /**
     * Returns the full blockchain as an ordered JSON array.
     *
     * <p>Each element is a {@link Block} serialized with its complete header,
     * transaction list, and hash fields. For large chains, consider adding
     * pagination via {@link #getBlock(int)} instead.</p>
     *
     * @return HTTP 200 with a JSON array of all blocks in ascending index order
     */
    @GetMapping("/chain")
    public ResponseEntity<List<Block>> getChain() {
        return ResponseEntity.ok(node.getChain().getChain());
    }

    /**
     * Returns a single block by its zero-based index.
     *
     * @param index the block index (&ge; 0)
     * @return HTTP 200 with the block, or HTTP 404 if no block exists at that index
     */
    @GetMapping("/chain/{index}")
    public ResponseEntity<Block> getBlock(@PathVariable int index) {
        try {
            return ResponseEntity.ok(node.getChain().getBlock(index));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Accepts a payment transaction request, signs it with the demo wallet, and
     * submits it to the mempool.
     *
     * <p>Request body:</p>
     * <pre>{@code
     * {
     *   "senderAddress": "1AbcDef...",
     *   "receiverAddress": "1XyzWvu...",
     *   "amount": 42.00,
     *   "currency": "USD"
     * }
     * }</pre>
     *
     * <p>Response (202 ACCEPTED):</p>
     * <pre>{@code
     * {
     *   "txId": "3f4a6b2c-...",
     *   "status": "PENDING",
     *   "mempoolSize": 1
     * }
     * }</pre>
     *
     * @param request the payment request body (non-null)
     * @return HTTP 202 ACCEPTED with the transaction ID on success,
     * HTTP 422 UNPROCESSABLE_CONTENT if validation fails
     */
    @PostMapping("/transactions")
    public ResponseEntity<Map<String, Object>> submitTransaction(
        @RequestBody TransactionRequest request) {

        Objects.requireNonNull(request, "request body must not be null");

        // Build a PaymentTransaction for the demo; in production this would be
        // a fully typed domain transaction class from the application layer
        PaymentTransaction tx = PaymentTransaction.of(
            demoWallet.getAddress(),    // use the demo wallet as the sender
            request.receiverAddress(),
            request.amount(),
            request.currency());

        // Sign the transaction with the demo wallet's private key
        demoWallet.sign(tx);

        try {
            // submitTransaction() runs configured validators, then adds to mempool
            node.submitTransaction(tx);
            return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(Map.of(
                    "txId", tx.getId().toString(),
                    "status", "PENDING",
                    "mempoolSize", node.getMempool().size()));

        } catch (TransactionValidationException e) {
            return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Triggers a manual block mining cycle, selecting up to 100 transactions from
     * the mempool and appending the resulting block to the chain.
     *
     * <p>Returns HTTP 204 NO_CONTENT if the mempool is empty (no block produced).
     * Returns HTTP 200 with block metadata on success.</p>
     *
     * <p>This endpoint exists for demo convenience — in a real node, mining runs
     * in a background thread or is triggered by a dedicated mining service.</p>
     *
     * @return HTTP 200 with block index, hash, and tx count on success;
     * HTTP 204 if the mempool is empty;
     * HTTP 500 if consensus or validation fails
     */
    @PostMapping("/mine")
    public ResponseEntity<Map<String, Object>> mineBlock() {
        List<Transaction> pending = node.getMempool().getTopN(100);

        if (pending.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        try {
            Block mined = node.getChain()
                .getConsensusEngine()
                .mineBlock(pending, node.getChain().getLatestBlock());

            // addBlock validates hash integrity, linkage, and consensus,
            // then persists and publishes BlockAddedEvent (removes confirmed txs from mempool)
            node.getChain().addBlock(mined);

            return ResponseEntity.ok(Map.of(
                "blockIndex", mined.getIndex(),
                "blockHash", mined.getHash(),
                "txCount", mined.getTransactions().size(),
                "chainHeight", node.status().chainHeight()));

        } catch (ConsensusException | BlockValidationException e) {
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }

    // ─── Request / value types ────────────────────────────────────────────────

    /**
     * Immutable request body for {@link #submitTransaction}.
     *
     * <p>Uses a Java 16+ record so Spring MVC can deserialize it via Jackson
     * without any additional configuration — records expose their components as
     * public accessor methods, which Jackson uses for both serialization and
     * deserialization when the constructor is annotated with {@link JsonCreator}.</p>
     *
     * @param receiverAddress destination blockchain address (non-null, non-blank)
     * @param amount          transfer amount (&ge; 0)
     * @param currency        ISO-4217 currency code (non-null, non-blank)
     */
    public record TransactionRequest(
        @JsonProperty("receiverAddress") String receiverAddress,
        @JsonProperty("amount") BigDecimal amount,
        @JsonProperty("currency") String currency) {

        /**
         * Compact canonical constructor — validates required fields.
         *
         * @throws NullPointerException     if any field is null
         * @throws IllegalArgumentException if any string field is blank or amount is negative
         */
        public TransactionRequest {
            Objects.requireNonNull(receiverAddress, "receiverAddress must not be null");
            Objects.requireNonNull(amount, "amount must not be null");
            Objects.requireNonNull(currency, "currency must not be null");
            if (receiverAddress.isBlank()) {
                throw new IllegalArgumentException("receiverAddress must not be blank");
            }
            if (currency.isBlank()) {
                throw new IllegalArgumentException("currency must not be blank");
            }
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("amount must be >= 0");
            }
        }
    }

    /**
     * Simple payment transaction used by the REST demo.
     *
     * <p>Defined as a private static inner class so this module does not need to
     * depend on {@code example-simple-chain} (which would create a cross-example
     * dependency). The {@link com.fasterxml.jackson.annotation.JsonTypeInfo} annotation
     * on {@link Transaction} writes the concrete class name ({@code _type}) into every
     * JSON payload, enabling full round-trips (AC-09).</p>
     */
    public static final class PaymentTransaction extends Transaction {

        /**
         * ISO-4217 currency code — included in the ECDSA signature.
         */
        private final String currency;

        /**
         * Jackson deserialization constructor.
         *
         * @param id              transaction UUID
         * @param senderAddress   sender address
         * @param receiverAddress receiver address
         * @param amount          transfer amount
         * @param timestamp       UTC creation time
         * @param metadata        arbitrary key-value metadata
         * @param currency        ISO-4217 currency code
         */
        @JsonCreator
        public PaymentTransaction(
            @JsonProperty("id") UUID id,
            @JsonProperty("senderAddress") String senderAddress,
            @JsonProperty("receiverAddress") String receiverAddress,
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("metadata") Map<String, Object> metadata,
            @JsonProperty("currency") String currency) {

            super(id, senderAddress, receiverAddress, amount, timestamp, metadata);
            this.currency = Objects.requireNonNull(currency, "currency must not be null");
        }

        /**
         * Factory for clean construction in the controller.
         *
         * @param sender   sender address
         * @param receiver receiver address
         * @param amount   transfer amount
         * @param currency ISO-4217 currency code
         * @return a new unsigned {@code PaymentTransaction}
         */
        public static PaymentTransaction of(
            String sender, String receiver, BigDecimal amount, String currency) {
            return new PaymentTransaction(
                UUID.randomUUID(), sender, receiver, amount, Instant.now(), null, currency);
        }

        /**
         * Returns the ISO-4217 currency code.
         *
         * @return non-null currency code string
         */
        public String getCurrency() {
            return currency;
        }

        /**
         * Extends the base signable bytes with {@code |currency} so the currency
         * field is cryptographically bound to the signature.
         *
         * @return signable byte array covering all fields including currency
         */
        @Override
        public byte[] toSignableBytes() {
            byte[] base = super.toSignableBytes();
            byte[] ext = ("|" + currency).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] combined = new byte[base.length + ext.length];
            System.arraycopy(base, 0, combined, 0, base.length);
            System.arraycopy(ext, 0, combined, base.length, ext.length);
            return combined;
        }
    }
}
