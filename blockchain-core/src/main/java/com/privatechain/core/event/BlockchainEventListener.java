package com.privatechain.core.event;

/**
 * Listener interface for receiving events from the {@link BlockchainEventBus}.
 *
 * <p>Register implementations with {@link BlockchainEventBus#register(BlockchainEventListener)}
 * to receive asynchronous notifications about blockchain state changes
 * (FR-EVENT-01, FR-EVENT-02).</p>
 *
 * <p>Event delivery is asynchronous and non-blocking: the triggering operation
 * (e.g., {@link com.privatechain.core.builder.Blockchain#addBlock}) completes
 * before listeners are invoked, so listeners must not assume they can
 * synchronously affect the outcome of the operation that generated the event
 * (FR-EVENT-03).</p>
 *
 * <h2>Implementation contract</h2>
 * <ul>
 *   <li>Implementations must not throw unchecked exceptions; any exception
 *       thrown is caught by the event bus and logged, then discarded.</li>
 *   <li>Long-running or blocking work (e.g., database writes, HTTP calls)
 *       should be offloaded to a separate executor to avoid delaying other
 *       listeners.</li>
 *   <li>Implementations must be thread-safe because {@link #onEvent} may be
 *       called from the event-bus delivery thread concurrently with other
 *       lifecycle methods.</li>
 * </ul>
 *
 * <h2>Usage example</h2>
 * <pre>{@code
 * eventBus.register(event -> {
 *     if (event instanceof BlockchainEvent.BlockAddedEvent added) {
 *         auditLog.record(added.getBlock());
 *     }
 * });
 * }</pre>
 *
 * @see BlockchainEventBus
 * @see BlockchainEvent
 * @since 1.0.0
 */
@FunctionalInterface
public interface BlockchainEventListener {

    /**
     * Called by the {@link BlockchainEventBus} when a blockchain event occurs.
     *
     * <p>The concrete event type can be determined via a sealed-type {@code switch}
     * expression or {@code instanceof} pattern matching. All known event types are
     * listed in {@link BlockchainEvent}.</p>
     *
     * @param event the event that occurred (non-null)
     */
    void onEvent(BlockchainEvent event);
}
