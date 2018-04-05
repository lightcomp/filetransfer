package com.lightcomp.ft.server;

/**
 * Internal receiver transfer state. <i>Implementation note: Do not change
 * defined order, ordinal is used.</i>
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
     * Data is transferring.
     */
    TRANSFERING,
    /**
     * Data is transfered, waiting for validation.
     */
    PREPARED,
    /**
     * Transfer is finished, second phase of two-phase commit.
     */
    COMMITTED,
    /**
     * File transfer is failed.
     */
    FAILED,
    /**
     * File transfer is canceled.
     */
    CANCELED;
}