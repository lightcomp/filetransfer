package com.lightcomp.ft.receiver;

/**
 * Internal receiver transfer state.
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