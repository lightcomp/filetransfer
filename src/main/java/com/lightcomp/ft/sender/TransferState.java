package com.lightcomp.ft.sender;

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
     * Data is transfered, waiting for validation.
     */
    TRANSFERED,
    /**
     * Data is validated, first phase of two-phase commit.
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
     * File transfer is canceled by sender.
     */
    CANCELED;
}