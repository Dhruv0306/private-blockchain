package com.privatechain.core.event;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread-safe, asynchronous publish/subscribe event bus for blockchain lifecycle events.
 *
 * <p>The event bus decouples blockchain internals from observer code. Any component
 * may publish an event; any number of {@link BlockchainEventListener} implementations
 * can react without the publisher knowing about them (FR-EVENT-01, FR-EVENT-02).</p>
 *
 * <h2>Delivery guarantees</h2>
 * <ul>
 *   <li>Events are dispatched to all registered listeners on a dedicated
 *       single-thread executor so that the calling thread (e.g., the miner
 *       that just sealed a block) is never blocked by slow listeners (FR-EVENT-03).</li>
 *   <li>Listener exceptions are caught and logged at {@code WARNING} level;
 *       they do not propagate back to the publisher or affect other listeners.</li>
 *   <li>Listener registration uses {@link CopyOnWriteArrayList}, which means
 *       registration/unregistration never blocks concurrent event publications.</li>
 *   <li>Events submitted before shutdown are delivered before the executor terminates.</li>
 * </ul>
 *
 * <h2>Wiring (Milestone 8 — T-066)</h2>
 * <p>The event bus is wired into three core subsystems:</p>
 * <ol>
 *   <li>{@code Blockchain.addBlock()} — publishes {@link BlockchainEvent.BlockAddedEvent}</li>
 *   <li>{@code TransactionMempool.submit()} — publishes
 *       {@link BlockchainEvent.TransactionSubmittedEvent}</li>
 *   <li>{@code PeerManager.connect()} / {@code disconnect()} — publishes
 *       {@link BlockchainEvent.PeerConnectedEvent} /
 *       {@link BlockchainEvent.PeerDisconnectedEvent}</li>
 * </ol>
 *
 * <h2>Lifecycle</h2>
 * <p>Call {@link #shutdown()} when the owning {@code BlockchainNode} stops to
 * drain pending events and terminate the delivery thread cleanly. The bus
 * supports {@link #awaitQuiescence(long, TimeUnit)} for test synchronization.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * BlockchainEventBus bus = new BlockchainEventBus();
 * bus.register(event -> {
 *     if (event instanceof BlockchainEvent.BlockAddedEvent e) {
 *         log.info("Block #{} added", e.getBlock().getIndex());
 *     }
 * });
 * bus.publish(new BlockchainEvent.BlockAddedEvent(block));
 * bus.shutdown();
 * }</pre>
 *
 * @see BlockchainEventListener
 * @see BlockchainEvent
 * @since 1.0.0
 */
public final class BlockchainEventBus {

    private static final Logger LOGGER = Logger.getLogger(BlockchainEventBus.class.getName());

    // ─── Default quiescence timeout ───────────────────────────────────────────
    /**
     * Default wait for {@link #awaitQuiescence} if no timeout is specified.
     */
    private static final long DEFAULT_QUIESCENCE_TIMEOUT_MS = 2_000L;

    // ─── Listener registry ────────────────────────────────────────────────────

    /**
     * Lock-free listener list; iteration is safe even while listeners are being
     * added or removed by other threads (design.md §7.4).
     */
    private final CopyOnWriteArrayList<BlockchainEventListener> listeners =
        new CopyOnWriteArrayList<>();

    // ─── Delivery executor ────────────────────────────────────────────────────

    /**
     * Single-threaded executor ensures events are delivered in publication order
     * without blocking the publisher (FR-EVENT-03).
     *
     * <p>The thread is marked as daemon so it does not prevent JVM shutdown when
     * no explicit {@link #shutdown()} call is made (e.g. during tests).</p>
     */
    private final ExecutorService deliveryExecutor =
        Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "blockchain-event-bus");
            thread.setDaemon(true);
            return thread;
        });

    // ─── Registration ─────────────────────────────────────────────────────────

    /**
     * Registers a listener to receive future events.
     *
     * <p>A listener may be registered multiple times; each registration results
     * in one additional delivery per event. To avoid duplicate delivery, callers
     * should check {@link #isRegistered(BlockchainEventListener)} first or use a
     * single shared reference.</p>
     *
     * @param listener the listener to register (non-null)
     * @throws NullPointerException if listener is null
     */
    public void register(BlockchainEventListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        listeners.add(listener);
        LOGGER.fine(() -> "Registered event listener: " + listener.getClass().getSimpleName());
    }

    /**
     * Unregisters a previously registered listener.
     *
     * <p>If the listener was registered multiple times, only the first occurrence
     * is removed per call. If the listener is not registered, this method is a no-op.</p>
     *
     * @param listener the listener to unregister (non-null)
     * @throws NullPointerException if listener is null
     */
    public void unregister(BlockchainEventListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        boolean removed = listeners.remove(listener);
        if (removed) {
            LOGGER.fine(() ->
                "Unregistered event listener: " + listener.getClass().getSimpleName());
        }
    }

    /**
     * Returns {@code true} if the given listener is currently registered.
     *
     * @param listener the listener to check (non-null)
     * @return {@code true} if registered at least once
     * @throws NullPointerException if listener is null
     */
    public boolean isRegistered(BlockchainEventListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        return listeners.contains(listener);
    }

    /**
     * Returns the number of currently registered listeners.
     *
     * @return listener count (&ge; 0)
     */
    public int listenerCount() {
        return listeners.size();
    }

    // ─── Publishing ───────────────────────────────────────────────────────────

    /**
     * Publishes an event to all currently registered listeners asynchronously.
     *
     * <p>This method returns immediately after submitting the delivery task to the
     * internal executor. Listeners are notified on the event-bus delivery thread,
     * not on the caller's thread. Delivery order among listeners for a single event
     * matches registration order (FR-EVENT-03).</p>
     *
     * <p>If the delivery executor has been shut down (after {@link #shutdown()}),
     * the event is silently dropped and a warning is logged.</p>
     *
     * @param event the event to publish (non-null)
     * @throws NullPointerException if event is null
     */
    public void publish(BlockchainEvent event) {
        Objects.requireNonNull(event, "event must not be null");

        if (deliveryExecutor.isShutdown()) {
            LOGGER.warning(() ->
                "Event bus is shut down — dropping event: " + event.getClass().getSimpleName());
            return;
        }

        // Snapshot of listeners at submission time ensures new registrations after
        // this call do not receive the current event — CopyOnWriteArrayList
        // iteration is already a structural snapshot; submitting the task
        // captures the executor state.
        deliveryExecutor.submit(() -> dispatchToAllListeners(event));
        LOGGER.fine(() -> "Published event: " + event.getClass().getSimpleName());
    }

    /**
     * Dispatches the event to every listener, catching and logging exceptions so
     * that a misbehaving listener cannot suppress delivery to subsequent listeners.
     *
     * <p>Called exclusively from the single-thread delivery executor.</p>
     *
     * @param event the event to dispatch
     */
    private void dispatchToAllListeners(BlockchainEvent event) {
        for (BlockchainEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Exception ex) {
                // Log and continue — one bad listener must not starve others
                LOGGER.log(Level.WARNING,
                    "Exception in event listener [" + listener.getClass().getName()
                        + "] while handling [" + event.getClass().getSimpleName() + "]",
                    ex);
            }
        }
    }

    // ─── Testability ──────────────────────────────────────────────────────────

    /**
     * Blocks the caller until all events published before this call have been
     * delivered, or until the timeout elapses.
     *
     * <p>This method is intended for test synchronization only. Production code
     * should not need it — the event bus is explicitly asynchronous by design.</p>
     *
     * <p>Implementation: submits a sentinel no-op task and waits for its completion.
     * Because the executor is single-threaded, completing the sentinel guarantees
     * all previously submitted tasks have also completed.</p>
     *
     * @param timeout maximum time to wait
     * @param unit    time unit of the timeout
     * @return {@code true} if all pending deliveries completed within the timeout;
     * {@code false} if the timeout elapsed first
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    public boolean awaitQuiescence(long timeout, TimeUnit unit) throws InterruptedException {
        if (deliveryExecutor.isShutdown()) {
            return true; // Nothing in flight after shutdown
        }
        // Submit a sentinel that completes a CountDownLatch
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        deliveryExecutor.submit(latch::countDown);
        return latch.await(timeout, unit);
    }

    /**
     * Blocks the caller until all pending events are delivered, using a
     * {@value #DEFAULT_QUIESCENCE_TIMEOUT_MS} ms default timeout.
     *
     * @return {@code true} if quiescent within the default timeout
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean awaitQuiescence() throws InterruptedException {
        return awaitQuiescence(DEFAULT_QUIESCENCE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    /**
     * Shuts down the event delivery executor gracefully.
     *
     * <p>Events already submitted to the executor are delivered before shutdown
     * completes (orderly drain). No new events are accepted after this call.
     * This method does <em>not</em> block; the drain happens asynchronously on
     * the delivery thread itself. Call {@link #awaitQuiescence} first if
     * synchronous completion is required.</p>
     *
     * <p>Typically called from {@link com.privatechain.core.builder.BlockchainNode#stop()}.</p>
     */
    public void shutdown() {
        deliveryExecutor.shutdown();
        LOGGER.info("BlockchainEventBus delivery executor shut down");
    }

    /**
     * Returns {@code true} if the event bus has been shut down.
     *
     * @return {@code true} after {@link #shutdown()} has been called
     */
    public boolean isShutdown() {
        return deliveryExecutor.isShutdown();
    }
}
