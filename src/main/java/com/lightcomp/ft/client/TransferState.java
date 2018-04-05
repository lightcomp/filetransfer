package com.lightcomp.ft.client;

/**
 * Internal sender transfer state.
 */
public enum TransferState {
    /**
     * Implicit state after creation.
     */
    INITIALIZED,
    /**
     * Data is transferring.
     */
    STARTED,
    /**
     * Data is transfered.
     */
    TRANSFERED,
    /**
     * Transfer is finished.
     */
    FINISHED,
    /**
     * File transfer is failed.
     */
    FAILED,
    /**
     * File transfer is canceled by sender.
     */
    CANCELED;
}