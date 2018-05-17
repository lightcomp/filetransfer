package com.lightcomp.ft.server;

/**
 * Internal transfer state. <i>Implementation note: Do not change defined order, ordinal is used.</i>
 */
public enum TransferState {
    /**
     * Implicit state after creation.
     */
    CREATED,
    /**
     * Data is transferring.
     */
    STARTED,
    /**
     * Data is transfered.
     */
    TRANSFERED,
    /**
     * Data is transfered, transfer waiting for response from acceptor.
     */
    FINISHING,
    /**
     * Transfer is finished.
     */
    FINISHED,
    /**
     * Transfer is failed.
     */
    FAILED,
    /**
     * Transfer is canceled.
     */
    CANCELED;

    /**
     * @return Returns true for FINISHED, FAILED and CANCELED states.
     */
    public boolean isTerminal() {
        return ordinal() >= FINISHED.ordinal();
    }
}