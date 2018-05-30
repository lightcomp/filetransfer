package com.lightcomp.ft.client;

/**
 * Internal transfer state.
 */
public enum TransferState {
    /**
     * Implicit state after creation.
     */
    CREATED,
    /**
     * Transfer was successfully started and data being transferred.
     */
    STARTED,
    /**
     * All data were transfered, waiting for transfer finish.
     */
    TRANSFERED,
    /**
     * Data is transfered, transfer waiting for response from server.
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
     * Transfer is canceled by client.
     */
    CANCELED;
}