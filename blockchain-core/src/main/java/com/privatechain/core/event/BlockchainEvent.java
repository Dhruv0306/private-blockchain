package com.privatechain.core.event;

import com.privatechain.core.model.Block;
import com.privatechain.core.model.Transaction;

import java.time.Instant;
import java.util.Objects;

/**
 * Sealed base type for all events published on the {@link BlockchainEventBus}.
 *
 * <p>The sealed hierarchy guarantees that every event variant is known at compile
 * time, enabling exhaustive {@code switch} expressions in consumer code (FR-EVENT-01).
 * New event types can only be added by the library maintainer, which is intentional:
 * it prevents external subclasses from breaking switch exhaustiveness.</p>
 *
 * <h2>Event variants</h2>
 * <ul>
 *   <li>{@link BlockAddedEvent} — a new block was appended to the chain</li>
 *   <li>{@link TransactionSubmittedEvent} — a transaction entered the mempool</li>
 *   <li>{@link PeerConnectedEvent} — a remote peer established a connection</li>
 *   <li>{@link PeerDisconnectedEvent} — a remote peer disconnected</li>
 *   <li>{@link ForkDetectedEvent} — two competing blocks at the same height were found</li>
 * </ul>
 *
 * <h2>Handling events</h2>
 * <pre>{@code
 * eventBus.register(event -> {
 *     if (event instanceof BlockchainEvent.BlockAddedEvent e) {
 *         log.info("Block #{} added", e.getBlock().getIndex());
 *     } else if (event instanceof BlockchainEvent.TransactionSubmittedEvent e) {
 *         metricsCounter.increment();
 *     }
 * });
 * }</pre>
 *
 * @since 1.0.0
 */
public abstract sealed class BlockchainEvent permits BlockchainEvent.BlockAddedEvent, BlockchainEvent.TransactionSubmittedEvent, BlockchainEvent.PeerConnectedEvent, BlockchainEvent.PeerDisconnectedEvent, BlockchainEvent.ForkDetectedEvent {

    /**
     * UTC instant at which the event was created.
     */
    private final Instant occurredAt;

    /**
     * Protected constructor for subclasses; sets {@code occurredAt} to the current time.
     */
    protected BlockchainEvent() {
        this.occurredAt = Instant.now();
    }

    /**
     * Validates that a string is neither null nor blank.
     *
     * @param value     the string to check
     * @param fieldName used in exception messages
     * @return the validated value
     */
    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    // ─── Permitted subclasses ─────────────────────────────────────────────────

    /**
     * Returns the UTC instant at which this event occurred.
     *
     * @return non-null event timestamp
     */
    public Instant getOccurredAt() {
        return occurredAt;
    }

    /**
     * Fired after a new block is successfully appended to the canonical chain.
     *
     * <p>Listeners receive this event after all validators and the storage write
     * have completed, so the chain is already in its new state when the listener runs.</p>
     *
     * @since 1.0.0
     */
    public static final class BlockAddedEvent extends BlockchainEvent {

        private final Block block;

        /**
         * Constructs a {@code BlockAddedEvent}.
         *
         * @param block the block that was added (non-null)
         * @throws NullPointerException if block is null
         */
        public BlockAddedEvent(Block block) {
            super();
            this.block = Objects.requireNonNull(block, "block must not be null");
        }

        /**
         * Returns the block that was added to the chain.
         *
         * @return non-null {@link Block}
         */
        public Block getBlock() {
            return block;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "BlockAddedEvent{blockIndex=" + block.getIndex() + ", hash=" + block.getHash() + '}';
        }
    }

    /**
     * Fired after a transaction successfully passes validation and enters the mempool.
     *
     * @since 1.0.0
     */
    public static final class TransactionSubmittedEvent extends BlockchainEvent {

        private final Transaction transaction;

        /**
         * Constructs a {@code TransactionSubmittedEvent}.
         *
         * @param transaction the transaction that was submitted (non-null)
         * @throws NullPointerException if transaction is null
         */
        public TransactionSubmittedEvent(Transaction transaction) {
            super();
            this.transaction = Objects.requireNonNull(transaction, "transaction must not be null");
        }

        /**
         * Returns the transaction that was submitted to the mempool.
         *
         * @return non-null {@link Transaction}
         */
        public Transaction getTransaction() {
            return transaction;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "TransactionSubmittedEvent{txId=" + transaction.getId() + '}';
        }
    }

    /**
     * Fired when a new peer successfully establishes a TCP connection to this node.
     *
     * <p>The peer has already passed the allowlist check at this point; the event
     * signals that the connection is active and the peer is ready to exchange messages.</p>
     *
     * @since 1.0.0
     */
    public static final class PeerConnectedEvent extends BlockchainEvent {

        private final String peerId;
        private final String address;

        /**
         * Constructs a {@code PeerConnectedEvent}.
         *
         * @param peerId  unique peer identifier (non-null, non-blank)
         * @param address network address string (non-null, non-blank)
         * @throws NullPointerException     if peerId or address is null
         * @throws IllegalArgumentException if peerId or address is blank
         */
        public PeerConnectedEvent(String peerId, String address) {
            super();
            this.peerId = requireNonBlank(peerId, "peerId");
            this.address = requireNonBlank(address, "address");
        }

        /**
         * Returns the unique identifier of the connected peer.
         *
         * @return non-null, non-blank peer ID
         */
        public String getPeerId() {
            return peerId;
        }

        /**
         * Returns the network address of the connected peer.
         *
         * @return non-null, non-blank address string
         */
        public String getAddress() {
            return address;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "PeerConnectedEvent{peerId=" + peerId + ", address=" + address + '}';
        }
    }

    /**
     * Fired when a peer connection is closed, either gracefully or due to an error.
     *
     * <p>Consumers can inspect {@link #getReason()} to distinguish between normal
     * disconnections and failures. A {@code null} reason means the cause is unknown.</p>
     *
     * @since 1.0.0
     */
    public static final class PeerDisconnectedEvent extends BlockchainEvent {

        private final String peerId;
        private final String reason;

        /**
         * Constructs a {@code PeerDisconnectedEvent}.
         *
         * @param peerId unique peer identifier (non-null, non-blank)
         * @param reason disconnection reason; may be null if unknown
         * @throws NullPointerException     if peerId is null
         * @throws IllegalArgumentException if peerId is blank
         */
        public PeerDisconnectedEvent(String peerId, String reason) {
            super();
            this.peerId = requireNonBlank(peerId, "peerId");
            this.reason = reason; // nullable — unknown reason is valid
        }

        /**
         * Returns the unique identifier of the disconnected peer.
         *
         * @return non-null, non-blank peer ID
         */
        public String getPeerId() {
            return peerId;
        }

        /**
         * Returns the human-readable reason for disconnection, or {@code null} if unknown.
         *
         * @return reason string or null
         */
        public String getReason() {
            return reason;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "PeerDisconnectedEvent{peerId=" + peerId + ", reason=" + reason + '}';
        }
    }

    // ─── Shared helper ────────────────────────────────────────────────────────

    /**
     * Fired when the node detects two competing valid blocks at the same chain height.
     *
     * <p>The {@link com.privatechain.core.builder.Blockchain} resolves forks by applying
     * the longest-chain (greatest cumulative difficulty) rule. This event is published
     * <em>before</em> resolution so that observers can log or alert on the fork.
     * Use {@link #getBlockA()} and {@link #getBlockB()} to inspect the competing blocks.</p>
     *
     * @since 1.0.0
     */
    public static final class ForkDetectedEvent extends BlockchainEvent {

        private final Block blockA;
        private final Block blockB;

        /**
         * Constructs a {@code ForkDetectedEvent}.
         *
         * @param blockA first competing block (non-null)
         * @param blockB second competing block (non-null)
         * @throws NullPointerException if either block is null
         */
        public ForkDetectedEvent(Block blockA, Block blockB) {
            super();
            this.blockA = Objects.requireNonNull(blockA, "blockA must not be null");
            this.blockB = Objects.requireNonNull(blockB, "blockB must not be null");
        }

        /**
         * Returns the first competing block.
         *
         * @return non-null {@link Block}
         */
        public Block getBlockA() {
            return blockA;
        }

        /**
         * Returns the second competing block.
         *
         * @return non-null {@link Block}
         */
        public Block getBlockB() {
            return blockB;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "ForkDetectedEvent{blockA=" + blockA.getHash() + ", blockB=" + blockB.getHash() + '}';
        }
    }
}
