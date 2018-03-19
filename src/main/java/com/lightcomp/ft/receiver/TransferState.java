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
     * Data are transferring.
     */
    STARTED,
    /**
     * Data transfered, waiting for validation.
     */
    TRANSFERED,
    /**
     * Data are validated, first phase of two-phase commit.
     */
    PREPARED,
    /**
     * Transfer is finished, second phase of two-phase commit.
     */
    COMMITTED,
    /**
     * File transfer failed.
     */
    FAILED,
    /**
     * File transfer canceled.
     */
    CANCELED
}