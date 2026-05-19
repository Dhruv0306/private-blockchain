package com.privatechain.core.mempool;

import com.privatechain.core.builder.Blockchain;
import com.privatechain.core.model.Transaction;
import com.privatechain.core.spi.TransactionPrioritizer;
import com.privatechain.core.spi.TransactionValidator;
import com.privatechain.core.spi.ValidationResult;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

/**
 * Thread-safe, in-memory pool of pending transactions awaiting block inclusion.
 *
 * <p>{@code TransactionMempool} maintains unconfirmed transactions in a
 * {@link PriorityQueue} ordered by a configurable {@link TransactionPrioritizer}.
 * Transactions are evicted after a configurable TTL via a background
 * {@link ScheduledExecutorService} task (FR-MEMPOOL-01 through FR-MEMPOOL-05).
 * Additionally, all submitted transactions pass through an optional
 * {@link TransactionValidator} gate (FR-MEMPOOL-06).</p>
 *
 * <h2>Validation</h2>
 * <p>When a transaction is submitted via {@link #submitWithValidation(Transaction, Blockchain)},
 * it is first validated against the current chain state. If validation fails, the
 * transaction is rejected and not added to the pool. This prevents invalid transactions
 * from lingering in memory and being propagated to peers.</p>
 *
 * <h2>Thread safety</h2>
 * <p>All public methods are protected by a {@link ReentrantLock}. The priority queue's
 * comparator must remain consistent during the entire pool lifetime; changing the
 * prioritizer dynamically is not supported.</p>
 *
 * <h2>Lifecycle</h2>
 * <p>Call {@link #start(Duration)} before submitting transactions and {@link #stop()}
 * on application shutdown to cleanly terminate the TTL eviction task.</p>
 *
 * @see TransactionPrioritizer
 * @see TransactionValidator
 * @since 1.0.0
 */
public final class TransactionMempool {

    private static final Logger LOGGER = Logger.getLogger(TransactionMempool.class.getName());

    // ─── Configuration ────────────────────────────────────────────────────────

    private final TransactionPrioritizer prioritizer;
    private final TransactionValidator validator; // Optional validator (FR-23)
    private final int maxPoolSize;

    // ─── State ────────────────────────────────────────────────────────────────

    private final PriorityQueue<Transaction> pool;
    private final Map<UUID, Long> submittedAtTime; // Track submission time for TTL
    private final ReentrantLock lock = new ReentrantLock();

    // ─── Eviction task ────────────────────────────────────────────────────────

    private ScheduledExecutorService evictionExecutor;
    private ScheduledFuture<?> evictionTask;

    // ─── Constructors ─────────────────────────────────────────────────────────

    /**
     * Constructs a new mempool with the given prioritizer, optional validator, and size limit.
     *
     * @param prioritizer the {@link TransactionPrioritizer} for ordering (non-null)
     * @param validator   optional {@link TransactionValidator} to gate submissions (nullable)
     * @param maxPoolSize maximum number of transactions before FIFO drops occur
     *                    (use {@link Integer#MAX_VALUE} for unlimited)
     * @throws NullPointerException if prioritizer is null
     */
    public TransactionMempool(TransactionPrioritizer prioritizer, TransactionValidator validator,
                              int maxPoolSize) {
        this.prioritizer = Objects.requireNonNull(prioritizer,
            "prioritizer must not be null");
        this.validator = validator; // May be null
        this.maxPoolSize = maxPoolSize > 0 ? maxPoolSize : Integer.MAX_VALUE;
        this.pool = new PriorityQueue<>(prioritizer);
        this.submittedAtTime = new ConcurrentHashMap<>();

        LOGGER.info(() -> "TransactionMempool initialised with prioritizer="
            + prioritizer.getClass().getSimpleName()
            + ", validator=" + (validator != null ? validator.getClass().getSimpleName() : "none")
            + ", maxPoolSize=" + this.maxPoolSize);
    }

    /**
     * Constructs a mempool with the given prioritizer and validator, unlimited size.
     *
     * @param prioritizer the {@link TransactionPrioritizer} for ordering (non-null)
     * @param validator   optional {@link TransactionValidator} (nullable)
     * @throws NullPointerException if prioritizer is null
     */
    public TransactionMempool(TransactionPrioritizer prioritizer, TransactionValidator validator) {
        this(prioritizer, validator, Integer.MAX_VALUE);
    }

    /**
     * Constructs a mempool with the given prioritizer and size limit (no validator).
     *
     * @param prioritizer the {@link TransactionPrioritizer} for ordering (non-null)
     * @param maxPoolSize maximum number of transactions
     * @throws NullPointerException if prioritizer is null
     */
    public TransactionMempool(TransactionPrioritizer prioritizer, int maxPoolSize) {
        this(prioritizer, null, maxPoolSize);
    }

    /**
     * Constructs a mempool with the given prioritizer (no validator, unlimited size).
     *
     * @param prioritizer the {@link TransactionPrioritizer} for ordering (non-null)
     * @throws NullPointerException if prioritizer is null
     */
    public TransactionMempool(TransactionPrioritizer prioritizer) {
        this(prioritizer, null, Integer.MAX_VALUE);
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    /**
     * Starts the background TTL eviction task.
     *
     * <p>This method must be called before any transactions are submitted. The
     * eviction task runs every {@code ttl / 2} seconds to remove stale transactions.</p>
     *
     * @param ttl the time-to-live duration for transactions in the pool
     *            (non-null, must be positive)
     * @throws NullPointerException     if ttl is null
     * @throws IllegalArgumentException if ttl duration is non-positive
     * @throws IllegalStateException    if already started
     */
    public void start(Duration ttl) {
        Objects.requireNonNull(ttl, "ttl must not be null");
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive, got: " + ttl);
        }

        lock.lock();
        try {
            if (evictionExecutor != null && !evictionExecutor.isShutdown()) {
                throw new IllegalStateException("Mempool already started");
            }

            evictionExecutor = Executors.newScheduledThreadPool(1, r -> {
                Thread t = new Thread(r, "MemPool-Eviction");
                t.setDaemon(true);
                return t;
            });

            long evictionPeriodMillis = Math.max(1000, ttl.toMillis() / 2);
            evictionTask = evictionExecutor.scheduleAtFixedRate(
                () -> evictExpired(ttl),
                evictionPeriodMillis,
                evictionPeriodMillis,
                TimeUnit.MILLISECONDS);

            LOGGER.info(() -> "TransactionMempool eviction started with ttl=" + ttl);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Stops the background eviction task and clears all transactions.
     *
     * <p>This method gracefully shuts down the executor, waiting up to 5 seconds
     * for in-flight eviction checks to complete.</p>
     *
     * @throws IllegalStateException if the mempool was never started
     */
    public void stop() {
        lock.lock();
        try {
            if (evictionExecutor == null || evictionExecutor.isShutdown()) {
                throw new IllegalStateException(
                    "Mempool was never started or already stopped");
            }

            evictionTask.cancel(false);
            evictionExecutor.shutdown();

            try {
                if (!evictionExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    evictionExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                evictionExecutor.shutdownNow();
            }

            pool.clear();
            submittedAtTime.clear();

            LOGGER.info("TransactionMempool stopped");
        } finally {
            lock.unlock();
        }
    }

    // ─── Submission with validation ────────────────────────────────────────────

    /**
     * Submits a transaction to the mempool after validating it against the blockchain.
     *
     * <p>If a validator is configured, the transaction is validated before entry.
     * Failing transactions are rejected and not added to the pool. If the pool is at
     * capacity, the lowest-priority transaction is evicted. Duplicate transactions
     * (by ID) are not re-added.</p>
     *
     * @param transaction the transaction to submit (non-null)
     * @param blockchain  the chain state for validation context (non-null if validator is present)
     * @return a {@link MempoolSubmissionResult} indicating success or validation failure
     * @throws NullPointerException if transaction is null, or if blockchain is null
     *                              and a validator is configured
     */
    public MempoolSubmissionResult submitWithValidation(Transaction transaction, Blockchain blockchain) {
        Objects.requireNonNull(transaction, "transaction must not be null");
        if (validator != null) {
            Objects.requireNonNull(blockchain, "blockchain must not be null when validator is configured");
        }

        // Perform validation (if validator is configured)
        if (validator != null) {
            ValidationResult validationResult = validator.validate(transaction, blockchain);
            if (validationResult.isFailure()) {
                LOGGER.fine(() -> "Transaction rejected by validator: " + transaction.getId()
                    + ", reason: " + validationResult.getStatus());
                return MempoolSubmissionResult.rejected(validationResult);
            }
        }

        // Add to pool
        lock.lock();
        try {
            // Reject if already present (by ID)
            if (submittedAtTime.containsKey(transaction.getId())) {
                LOGGER.fine(() -> "Transaction already in pool: " + transaction.getId());
                return MempoolSubmissionResult.duplicate();
            }

            // If at capacity, evict the lowest-priority transaction
            if (pool.size() >= maxPoolSize) {
                Transaction evicted = pool.poll();
                if (evicted != null) {
                    submittedAtTime.remove(evicted.getId());
                    LOGGER.fine(() -> "Evicted low-priority tx: " + evicted.getId());
                }
            }

            // Add the new transaction
            pool.offer(transaction);
            submittedAtTime.put(transaction.getId(), System.currentTimeMillis());

            LOGGER.fine(() -> "Transaction submitted: " + transaction.getId()
                + ", pool size=" + pool.size());
            return MempoolSubmissionResult.accepted();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Submits a transaction to the mempool without validation.
     *
     * <p>This method bypasses any configured validator and is useful for testing
     * or when the caller has already performed validation. For normal use, prefer
     * {@link #submitWithValidation(Transaction, Blockchain)}.</p>
     *
     * <p>If the pool reaches {@link #maxPoolSize}, the transaction with the lowest
     * priority (tail of the priority queue) is evicted to make room. If the same
     * transaction (by ID) already exists, it is not re-added.</p>
     *
     * @param transaction the transaction to add (non-null)
     * @return {@code true} if the transaction was added; {@code false} if rejected
     * (duplicate or pool at capacity)
     * @throws NullPointerException if transaction is null
     */
    public boolean submit(Transaction transaction) {
        Objects.requireNonNull(transaction, "transaction must not be null");

        lock.lock();
        try {
            // Reject if already present (by ID)
            if (submittedAtTime.containsKey(transaction.getId())) {
                LOGGER.fine(() -> "Transaction already in pool: " + transaction.getId());
                return false;
            }

            // If at capacity, evict the lowest-priority transaction
            if (pool.size() >= maxPoolSize) {
                Transaction evicted = pool.poll();
                if (evicted != null) {
                    submittedAtTime.remove(evicted.getId());
                    LOGGER.fine(() -> "Evicted low-priority tx: " + evicted.getId());
                }
            }

            // Add the new transaction
            pool.offer(transaction);
            submittedAtTime.put(transaction.getId(), System.currentTimeMillis());

            LOGGER.fine(() -> "Transaction submitted: " + transaction.getId()
                + ", pool size=" + pool.size());
            return true;
        } finally {
            lock.unlock();
        }
    }

    // ─── Queries ───────────────────────────────────────────────────────────────

    /**
     * Returns the top N transactions ordered by priority (highest priority first).
     *
     * <p>The returned list is a snapshot; the mempool is not modified.</p>
     *
     * @param n the number of top transactions to return
     * @return a list of up to {@code n} transactions (may have fewer if pool is smaller)
     * @throws IllegalArgumentException if n is negative
     */
    public List<Transaction> getTopN(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("n must be >= 0, got: " + n);
        }

        lock.lock();
        try {
            List<Transaction> result = new ArrayList<>();
            int count = 0;

            // Create a temporary list to preserve pool state
            List<Transaction> temp = new ArrayList<>(pool);

            for (Transaction tx : temp) {
                if (count >= n) {
                    break;
                }
                result.add(tx);
                count++;
            }

            return result;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the current number of transactions in the pool.
     *
     * @return non-negative integer
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
     * @param transactionId the transaction ID to check (non-null)
     * @return {@code true} if the transaction is in the pool
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
     * Removes a transaction from the pool (e.g., after it is confirmed in a block).
     *
     * @param transactionId the ID of the transaction to remove (non-null)
     * @return {@code true} if the transaction was present and removed
     */
    public boolean remove(UUID transactionId) {
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        lock.lock();
        try {
            // Find and remove the transaction by ID
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
     * Evicts transactions older than the given TTL.
     *
     * <p>This method is called automatically by the background eviction task.
     * Callers may also invoke it manually to trigger an immediate cleanup pass.</p>
     *
     * @param ttl the time-to-live duration (non-null)
     */
    public void evictExpired(Duration ttl) {
        Objects.requireNonNull(ttl, "ttl must not be null");

        lock.lock();
        try {
            long now = System.currentTimeMillis();
            long ttlMillis = ttl.toMillis();

            // Use entrySet iterator for efficient key-value access
            Iterator<Map.Entry<UUID, Long>> iter = submittedAtTime.entrySet().iterator();
            int evicted = 0;

            while (iter.hasNext()) {
                Map.Entry<UUID, Long> entry = iter.next();
                UUID txId = entry.getKey();
                long submittedAt = entry.getValue();

                if (now - submittedAt > ttlMillis) {
                    pool.removeIf(tx -> tx.getId().equals(txId));
                    iter.remove();
                    evicted++;
                }
            }

            if (evicted > 0) {
                int finalEvicted = evicted;
                LOGGER.fine(() -> "Evicted " + finalEvicted + " expired transactions");
            }
        } finally {
            lock.unlock();
        }
    }
}

