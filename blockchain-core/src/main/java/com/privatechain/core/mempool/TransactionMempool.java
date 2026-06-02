package com.privatechain.core.mempool;

import com.privatechain.core.builder.Blockchain;
import com.privatechain.core.event.BlockchainEvent;
import com.privatechain.core.event.BlockchainEventBus;
import com.privatechain.core.event.BlockchainEventListener;
import com.privatechain.core.model.Transaction;
import com.privatechain.core.spi.TransactionPrioritizer;
import com.privatechain.core.spi.TransactionValidator;
import com.privatechain.core.spi.ValidationResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

/**
 * In-memory pool of submitted, unconfirmed transactions ordered by a pluggable
 * {@link TransactionPrioritizer} (FR-MEMPOOL-01 through FR-MEMPOOL-05).
 *
 * <h2>Milestone 8 wiring (T-066)</h2>
 * <p>When constructed with a {@link BlockchainEventBus} the mempool automatically:</p>
 * <ol>
 *   <li>Publishes a {@link BlockchainEvent.TransactionSubmittedEvent} on every
 *       successful {@link #submit(Transaction)} call.</li>
 *   <li>Registers an internal {@link BlockchainEventListener} that listens for
 *       {@link BlockchainEvent.BlockAddedEvent} and removes all transactions that
 *       were confirmed in that block (FR-MEMPOOL-05).</li>
 * </ol>
 *
 * <h2>Thread safety</h2>
 * <p>All public methods are protected by a {@link ReentrantLock}. The event listener
 * callback {@code onEvent()} also acquires the same lock before modifying the pool,
 * so concurrent submissions and block-confirmations are always safe.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * TransactionMempool mempool = new TransactionMempool(
 *     new FeeBasedPrioritizer(), eventBus);
 * mempool.start(Duration.ofMinutes(10)); // enable TTL eviction
 *
 * MempoolSubmissionResult result = mempool.submitWithValidation(tx, blockchain);
 * if (result.isAccepted()) {
 *     // tx is in the pool and TransactionSubmittedEvent has been published
 * }
 * }</pre>
 *
 * @see FeeBasedPrioritizer
 * @see TimestampBasedPrioritizer
 * @since 1.0.0
 */
public final class TransactionMempool {

    private static final Logger LOGGER = Logger.getLogger(TransactionMempool.class.getName());

    /** Default maximum pool capacity (unbounded). */
    private static final int DEFAULT_MAX_SIZE = Integer.MAX_VALUE;

    // ─── Dependencies ─────────────────────────────────────────────────────────

    /** Ordering strategy applied by {@link PriorityQueue} and {@link #getTopN(int)}. */
    private final TransactionPrioritizer prioritizer;

    /** Optional validator applied in {@link #submitWithValidation}. */
    private final TransactionValidator validator;

    /**
     * Optional event bus for publishing {@link BlockchainEvent.TransactionSubmittedEvent}
     * and receiving {@link BlockchainEvent.BlockAddedEvent} for confirmed-tx cleanup.
     * {@code null} in standalone mode (no event wiring).
     */
    private final BlockchainEventBus eventBus;

    // ─── Pool state ───────────────────────────────────────────────────────────

    /**
     * Priority queue ordering transactions by the configured prioritizer.
     * Access always under {@link #lock}.
     */
    private final PriorityQueue<Transaction> pool;

    /**
     * Tracks when each transaction was submitted (epoch millis) for TTL eviction.
     * Uses insertion-ordered {@link LinkedHashMap} for predictable eviction iteration.
     */
    private final Map<UUID, Long> submittedAtTime = new LinkedHashMap<>();

    /** Maximum number of transactions the pool can hold at once. */
    private final int maxSize;

    /** Guards all pool mutations and reads. */
    private final ReentrantLock lock = new ReentrantLock();

    /** Background eviction scheduler; {@code null} until {@link #start} is called. */
    private ScheduledExecutorService evictionScheduler;

    // ─── Constructors ─────────────────────────────────────────────────────────

    /**
     * Creates a mempool with a prioritizer and no event-bus integration.
     *
     * @param prioritizer ordering strategy for pending transactions (non-null)
     * @throws NullPointerException if prioritizer is null
     */
    public TransactionMempool(TransactionPrioritizer prioritizer) {
        this(prioritizer, null, DEFAULT_MAX_SIZE);
    }

    /**
     * Creates a mempool with a prioritizer, event-bus integration, and
     * the default unbounded capacity.
     *
     * <p>When {@code eventBus} is non-null the mempool:</p>
     * <ul>
     *   <li>Publishes {@link BlockchainEvent.TransactionSubmittedEvent} on {@link #submit}.</li>
     *   <li>Registers a listener that removes confirmed transactions on
     *       {@link BlockchainEvent.BlockAddedEvent}.</li>
     * </ul>
     *
     * @param prioritizer ordering strategy for pending transactions (non-null)
     * @param eventBus    event bus to wire into; may be {@code null} for standalone mode
     * @throws NullPointerException if prioritizer is null
     */
    public TransactionMempool(TransactionPrioritizer prioritizer, BlockchainEventBus eventBus) {
        this(prioritizer, null, DEFAULT_MAX_SIZE, eventBus);
    }

    /**
     * Creates a mempool with a prioritizer, optional validator, and unbounded capacity.
     *
     * @param prioritizer ordering strategy (non-null)
     * @param validator   pre-submit validator; may be {@code null}
     * @throws NullPointerException if prioritizer is null
     */
    public TransactionMempool(TransactionPrioritizer prioritizer, TransactionValidator validator) {
        this(prioritizer, validator, DEFAULT_MAX_SIZE);
    }

    /**
     * Creates a mempool with a prioritizer, no validator, and bounded capacity.
     *
     * @param prioritizer ordering strategy (non-null)
     * @param maxSize     maximum pool capacity (&ge; 1)
     * @throws NullPointerException     if prioritizer is null
     * @throws IllegalArgumentException if maxSize &lt; 1
     */
    public TransactionMempool(TransactionPrioritizer prioritizer, int maxSize) {
        this(prioritizer, null, maxSize);
    }

    /**
     * Creates a mempool with a prioritizer, optional validator, bounded capacity,
     * and no event-bus integration.
     *
     * @param prioritizer ordering strategy (non-null)
     * @param validator   pre-submit validator; may be {@code null}
     * @param maxSize     maximum pool capacity (&ge; 1)
     * @throws NullPointerException     if prioritizer is null
     * @throws IllegalArgumentException if maxSize &lt; 1
     */
    public TransactionMempool(
        TransactionPrioritizer prioritizer, TransactionValidator validator, int maxSize) {
        this(prioritizer, validator, maxSize, null);
    }

    /**
     * Full constructor wiring all dependencies.
     *
     * @param prioritizer ordering strategy (non-null)
     * @param validator   pre-submit validator; may be {@code null}
     * @param maxSize     maximum pool capacity (&ge; 1)
     * @param eventBus    event bus; may be {@code null}
     * @throws NullPointerException     if prioritizer is null
     * @throws IllegalArgumentException if maxSize &lt; 1
     */
    public TransactionMempool(
        TransactionPrioritizer prioritizer,
        TransactionValidator validator,
        int maxSize,
        BlockchainEventBus eventBus) {

        this.prioritizer = Objects.requireNonNull(prioritizer, "prioritizer must not be null");
        this.validator = validator;  // nullable — optional gate
        if (maxSize < 1) {
            throw new IllegalArgumentException("maxSize must be >= 1, got: " + maxSize);
        }
        this.maxSize = maxSize;
        this.eventBus = eventBus;

        // PriorityQueue ordered by the injected prioritizer (highest priority = head)
        this.pool = new PriorityQueue<>(prioritizer);

        // Wire into event bus if provided (FR-MEMPOOL-05, T-066)
        if (eventBus != null) {
            eventBus.register(new BlockAddedEventListener());
            LOGGER.fine("TransactionMempool registered as BlockAddedEvent listener on event bus");
        }

        LOGGER.fine(() -> "TransactionMempool initialised [maxSize=" + maxSize
            + ", prioritizer=" + prioritizer.getClass().getSimpleName()
            + ", eventBus=" + (eventBus != null ? "wired" : "none") + "]");
    }

    // ─── Submission ───────────────────────────────────────────────────────────

    /**
     * Submits a transaction to the pool without pre-validation.
     *
     * <p>The transaction is rejected if:</p>
     * <ul>
     *   <li>The pool already contains a transaction with the same ID (duplicate)</li>
     *   <li>The pool is at capacity and the new transaction has lower priority than
     *       all existing transactions (lowest-priority eviction applies)</li>
     * </ul>
     *
     * <p>On acceptance, if an event bus is wired, a
     * {@link BlockchainEvent.TransactionSubmittedEvent} is published asynchronously.</p>
     *
     * @param transaction the transaction to submit (non-null)
     * @return {@code true} if the transaction was accepted; {@code false} if rejected
     * @throws NullPointerException if transaction is null
     */
    public boolean submit(Transaction transaction) {
        Objects.requireNonNull(transaction, "transaction must not be null");

        lock.lock();
        try {
            // Reject duplicates (FR-MEMPOOL-01)
            if (submittedAtTime.containsKey(transaction.getId())) {
                LOGGER.fine(() ->
                    "Duplicate transaction rejected: " + transaction.getId());
                return false;
            }

            // If at capacity, evict the lowest-priority entry to make room
            if (pool.size() >= maxSize) {
                evictLowestPriority(transaction);
                // If still at capacity (new tx is the lowest), reject it
                if (pool.size() >= maxSize) {
                    LOGGER.fine(() ->
                        "Pool full — transaction rejected (lower priority than all "
                            + "existing): " + transaction.getId());
                    return false;
                }
            }

            pool.offer(transaction);
            submittedAtTime.put(transaction.getId(), System.currentTimeMillis());
            LOGGER.fine(() -> "Transaction accepted into pool: " + transaction.getId()
                + " [poolSize=" + pool.size() + "]");

        } finally {
            lock.unlock();
        }

        // Publish event outside the lock to avoid holding it during async dispatch
        publishSubmittedEvent(transaction);
        return true;
    }

    /**
     * Validates and submits a transaction using the configured {@link TransactionValidator}.
     *
     * <p>If no validator was provided at construction time, this behaves identically
     * to {@link #submit(Transaction)}.</p>
     *
     * @param transaction the transaction to validate and submit (non-null)
     * @param chain       the current blockchain state used during validation (non-null)
     * @return a {@link MempoolSubmissionResult} describing the outcome
     * @throws NullPointerException if transaction or chain is null
     */
    public MempoolSubmissionResult submitWithValidation(Transaction transaction, Blockchain chain) {
        Objects.requireNonNull(transaction, "transaction must not be null");
        Objects.requireNonNull(chain, "chain must not be null");

        if (validator != null) {
            ValidationResult result = validator.validate(transaction, chain);
            if (result.isFailure()) {
                LOGGER.fine(() ->
                    "Transaction failed validation [id=" + transaction.getId()
                        + ", errors=" + result.getErrors() + "]");
                return MempoolSubmissionResult.rejected(result.getErrors());
            }
        }

        boolean accepted = submit(transaction);
        if (accepted) {
            return MempoolSubmissionResult.accepted();
        }
        // submit() handles duplicates / capacity — return a generic rejection
        return MempoolSubmissionResult.rejected(
            List.of("Rejected: duplicate or pool capacity exceeded"));
    }

    // ─── Retrieval ────────────────────────────────────────────────────────────

    /**
     * Returns the top {@code n} transactions ordered by the configured prioritizer.
     *
     * <p>The returned list is a snapshot; modifications to the pool do not affect it.</p>
     *
     * @param n maximum number of transactions to return (&ge; 0)
     * @return ordered list with at most {@code n} entries
     * @throws IllegalArgumentException if n is negative
     */
    public List<Transaction> getTopN(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("n must be >= 0, got: " + n);
        }
        if (n == 0) {
            return List.of();
        }

        lock.lock();
        try {
            // Drain to a sorted list and return the first n entries
            List<Transaction> all = new ArrayList<>(pool);
            all.sort(prioritizer);
            return List.copyOf(all.subList(0, Math.min(n, all.size())));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the current number of transactions in the pool.
     *
     * @return pool size (&ge; 0)
     */
    public int size() {
        lock.lock();
        try {
            return pool.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns {@code true} if the pool contains a transaction with the given ID.
     *
     * @param transactionId the ID to look up (non-null)
     * @return {@code true} if present
     * @throws NullPointerException if transactionId is null
     */
    public boolean contains(UUID transactionId) {
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        lock.lock();
        try {
            return submittedAtTime.containsKey(transactionId);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes a specific transaction from the pool.
     *
     * @param transactionId the ID of the transaction to remove (non-null)
     * @return {@code true} if the transaction was found and removed
     * @throws NullPointerException if transactionId is null
     */
    public boolean remove(UUID transactionId) {
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        lock.lock();
        try {
            boolean removed = pool.removeIf(tx -> tx.getId().equals(transactionId));
            if (removed) {
                submittedAtTime.remove(transactionId);
                LOGGER.fine(() -> "Transaction removed from pool: " + transactionId);
            }
            return removed;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes all transactions from the pool.
     */
    public void clear() {
        lock.lock();
        try {
            pool.clear();
            submittedAtTime.clear();
            LOGGER.fine("TransactionMempool cleared");
        } finally {
            lock.unlock();
        }
    }

    // ─── TTL eviction ─────────────────────────────────────────────────────────

    /**
     * Evicts transactions older than the given TTL (FR-MEMPOOL-04).
     *
     * <p>Called automatically by the background scheduler after {@link #start} is
     * invoked. May also be called manually for an immediate eviction pass.</p>
     *
     * @param ttl the time-to-live duration (non-null, positive)
     * @throws NullPointerException if ttl is null
     */
    public void evictExpired(Duration ttl) {
        Objects.requireNonNull(ttl, "ttl must not be null");

        lock.lock();
        try {
            long now = System.currentTimeMillis();
            long ttlMillis = ttl.toMillis();
            int evicted = 0;

            Iterator<Map.Entry<UUID, Long>> iter = submittedAtTime.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<UUID, Long> entry = iter.next();
                if (now - entry.getValue() > ttlMillis) {
                    pool.removeIf(tx -> tx.getId().equals(entry.getKey()));
                    iter.remove();
                    evicted++;
                }
            }

            if (evicted > 0) {
                int finalEvicted = evicted;
                LOGGER.fine(() -> "Evicted " + finalEvicted + " expired transactions from pool");
            }
        } finally {
            lock.unlock();
        }
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    /**
     * Starts the background TTL eviction scheduler.
     *
     * <p>After this call, {@link #evictExpired(Duration)} is called at a fixed
     * interval equal to {@code ttl / 2} (minimum 10 seconds). The scheduler runs
     * as a daemon thread and stops when {@link #stop()} is called.</p>
     *
     * @param ttl the time-to-live for unconfirmed transactions (non-null, positive)
     * @throws NullPointerException if ttl is null
     * @throws IllegalStateException if the scheduler is already running
     */
    public void start(Duration ttl) {
        Objects.requireNonNull(ttl, "ttl must not be null");
        if (evictionScheduler != null && !evictionScheduler.isShutdown()) {
            throw new IllegalStateException("Eviction scheduler is already running");
        }

        long periodMs = Math.max(ttl.toMillis() / 2L, 10_000L);

        evictionScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "blockchain-mempool-eviction");
            t.setDaemon(true);
            return t;
        });

        evictionScheduler
            .scheduleAtFixedRate(
                () -> evictExpired(ttl),
                periodMs,
                periodMs,
                TimeUnit.MILLISECONDS);

        LOGGER.info(() ->
            "TransactionMempool eviction scheduler started [ttl=" + ttl
                + ", interval=" + Duration.ofMillis(periodMs) + "]");
    }

    /**
     * Stops the background eviction scheduler and clears the pool.
     *
     * <p>After this call, no further automatic eviction occurs. The pool is
     * cleared so that stale references are not retained after node shutdown.</p>
     */
    public void stop() {
        if (evictionScheduler != null) {
            evictionScheduler.shutdown();
            evictionScheduler = null;
        }
        clear();
        LOGGER.info("TransactionMempool stopped and cleared");
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    /**
     * Evicts the lowest-priority transaction from the pool to make room for
     * {@code candidate}. If the candidate itself has lower priority than the
     * current lowest-priority entry, no eviction is performed (the caller
     * will then reject the candidate).
     *
     * <p>Must be called while {@link #lock} is held.</p>
     *
     * @param candidate the incoming transaction competing for pool space
     */
    private void evictLowestPriority(Transaction candidate) {
        // Find the lowest-priority transaction by reversing the comparator
        Transaction lowest = pool.stream()
            .max(prioritizer)
            .orElse(null);

        if (lowest == null) {
            return;
        }

        // Only evict if candidate has strictly higher priority
        if (prioritizer.compare(candidate, lowest) < 0) {
            pool.remove(lowest);
            submittedAtTime.remove(lowest.getId());
            LOGGER.fine(() ->
                "Evicted lowest-priority transaction from pool [id=" + lowest.getId()
                    + "] to make room for [id=" + candidate.getId() + "]");
        }
    }

    /**
     * Publishes a {@link BlockchainEvent.TransactionSubmittedEvent} asynchronously
     * to the wired event bus (if any). Called outside the pool lock.
     *
     * @param transaction the transaction that was accepted
     */
    private void publishSubmittedEvent(Transaction transaction) {
        if (eventBus != null && !eventBus.isShutdown()) {
            eventBus.publish(new BlockchainEvent.TransactionSubmittedEvent(transaction));
        }
    }

    /**
     * Removes all transactions confirmed in the given block from the pool.
     *
     * <p>Called by {@link BlockAddedEventListener} when a
     * {@link BlockchainEvent.BlockAddedEvent} is received (FR-MEMPOOL-05).</p>
     *
     * @param block the block whose transactions should be removed from the pool
     */
    private void removeConfirmedTransactions(com.privatechain.core.model.Block block) {
        if (block.getTransactions().isEmpty()) {
            return;
        }

        lock.lock();
        try {
            int removedCount = 0;
            for (Transaction tx : block.getTransactions()) {
                boolean removed = pool.removeIf(p -> p.getId().equals(tx.getId()));
                if (removed) {
                    submittedAtTime.remove(tx.getId());
                    removedCount++;
                }
            }
            if (removedCount > 0) {
                int finalCount = removedCount;
                LOGGER.fine(() ->
                    "Removed " + finalCount + " confirmed transaction(s) from mempool "
                        + "after block #" + block.getIndex() + " was added");
            }
        } finally {
            lock.unlock();
        }
    }

    // ─── Inner: BlockAddedEvent listener (T-066, FR-MEMPOOL-05) ──────────────

    /**
     * Internal listener that removes confirmed transactions from the mempool
     * whenever a new block is committed to the chain.
     *
     * <p>Registered on the {@link BlockchainEventBus} at construction time when
     * an event bus is provided. Only reacts to {@link BlockchainEvent.BlockAddedEvent};
     * all other event types are ignored efficiently.</p>
     */
    private final class BlockAddedEventListener implements BlockchainEventListener {

        /**
         * Handles an incoming event from the event bus.
         *
         * @param event the event to process (non-null)
         */
        @Override
        public void onEvent(BlockchainEvent event) {
            if (event instanceof BlockchainEvent.BlockAddedEvent blockAdded) {
                removeConfirmedTransactions(blockAdded.getBlock());
            }
            // Silently ignore all other event types
        }
    }
}
