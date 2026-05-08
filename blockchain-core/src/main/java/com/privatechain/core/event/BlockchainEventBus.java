package com.privatechain.core.event;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread-safe, asynchronous publish/subscribe event bus for blockchain events.
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
 * </ul>
 *
 * <h2>Lifecycle</h2>
 * <p>Call {@link #shutdown()} when the owning {@code BlockchainNode} stops to
 * drain pending events and terminate the delivery thread cleanly.</p>
 *
 * @see BlockchainEventListener
 * @see BlockchainEvent
 * @since 1.0.0
 */
public final class BlockchainEventBus {

    private static final Logger LOGGER = Logger.getLogger(BlockchainEventBus.class.getName());

    /**
     * Lock-free listener list; iteration is safe even while listeners are being
     * added or removed by other threads.
     */
    private final CopyOnWriteArrayList<BlockchainEventListener> listeners =
        new CopyOnWriteArrayList<>();

    /**
     * Single-threaded executor ensures events are delivered in publication order
     * without blocking the publisher.
     */
    private final ExecutorService deliveryExecutor =
        Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "blockchain-event-bus");
            thread.setDaemon(true); // don't prevent JVM shutdown
            return thread;
        });

    // ─── Registration ─────────────────────────────────────────────────────────

    /**
     * Registers a listener to receive future events.
     *
     * <p>A listener may be registered multiple times; each registration results
     * in one additional delivery per event. Registration is idempotent only if
     * the same object reference is used.</p>
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
            LOGGER.fine(() -> "Unregistered event listener: " + listener.getClass().getSimpleName());
        }
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
     * matches registration order.</p>
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
            LOGGER.warning("Event bus is shut down — dropping event: " + event);
            return;
        }

        // Capture snapshot of listeners at publish time (CopyOnWriteArrayList is safe here)
        deliveryExecutor.submit(() -> dispatchToAllListeners(event));
    }

    /**
     * Dispatches the event to every listener, catching and logging exceptions so
     * that a misbehaving listener cannot suppress delivery to subsequent listeners.
     *
     * @param event the event to dispatch
     */
    private void dispatchToAllListeners(BlockchainEvent event) {
        for (BlockchainEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Exception ex) {
                // Log and continue — one bad listener must not break others
                LOGGER.log(Level.WARNING,
                    "Exception in event listener " + listener.getClass().getName()
                        + " while handling " + event.getClass().getSimpleName(),
                    ex);
            }
        }
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    /**
     * Shuts down the event delivery executor.
     *
     * <p>Pending events already submitted to the executor are delivered before shutdown
     * completes. No new events are accepted after this method is called. This method
     * blocks until all pending deliveries complete or the current thread is interrupted.</p>
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
