package com.privatechain.core.network;

/**
 * Contract for the optional startup chain synchronisation component.
 *
 * <p>Defined in {@code blockchain-core} so that {@link com.privatechain.core.builder.BlockchainNode}
 * can trigger an initial sync without depending on {@code blockchain-network}.
 * The concrete implementation {@code com.privatechain.network.sync.SyncManager}
 * implements this interface.</p>
 *
 * @since 1.0.0
 */
public interface ChainSyncer {

    /**
     * Synchronizes the local chain against the connected peer network.
     *
     * @return the number of blocks appended during this sync session (&ge; 0)
     */
    int syncChain();
}
