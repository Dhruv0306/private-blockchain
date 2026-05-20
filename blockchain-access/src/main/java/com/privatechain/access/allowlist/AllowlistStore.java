package com.privatechain.access.allowlist;

import java.util.Set;

/**
 * Interface for persisting the allowlist across restarts.
 *
 * <p>This SPI allows implementations to store allowlist changes durably
 * to disk, a database, or other persistent storage. By default,
 * {@link AllowlistManager} works in-memory; calls to {@link AllowlistManager#add(String)}
 * and {@link AllowlistManager#remove(String)} can optionally invoke an {@code AllowlistStore}
 * to ensure durability.</p>
 *
 * <h2>Implementation requirements</h2>
 * <ul>
 *   <li>The store MUST be thread-safe for concurrent access.</li>
 *   <li>Writes MUST be durable and survive process restarts.</li>
 *   <li>Load operations MUST be idempotent.</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * AllowlistStore fileStore = new FileAllowlistStore(Paths.get("/data/allowlist.txt"));
 * AllowlistManager allowlist = new AllowlistManager();
 * allowlist.loadFromStore(fileStore);  // Load previously persisted allowlist
 *
 * allowlist.add("new-node");
 * allowlist.saveToStore(fileStore);    // Durably save the update
 * }</pre>
 *
 * @see AllowlistManager
 * @since 1.0.0
 */
public interface AllowlistStore {

    /**
     * Loads allowlisted node IDs from persistent storage.
     *
     * <p>This method is typically called once at startup to restore the allowlist
     * from a previous session.</p>
     *
     * @return a set of allowlisted node IDs (maybe empty, but non-null)
     */
    Set<String> load();

    /**
     * Saves a set of node IDs to persistent storage, replacing any previously stored list.
     *
     * <p>This method is called whenever the allowlist is modified (in {@link AllowlistManager}
     * integration scenarios) to ensure durability.</p>
     *
     * @param nodeIds the set of node IDs to persist (non-null, may be empty)
     */
    void save(Set<String> nodeIds);
}

