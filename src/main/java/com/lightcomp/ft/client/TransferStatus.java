package com.lightcomp.ft.client;

import java.time.LocalDateTime;

/**
 * File transfer status.
 */
public interface TransferStatus {

    /**
     * Transfer state.
     */
    TransferState getState();

    /**
     * Last time of any transfer activity.
     */
    LocalDateTime getLastActivity();

    /**
     * Transfer start time.
     */
    LocalDateTime getStartTime();

    /**
     * Retry count of communication with server.
     */
    int getRetryCount();

    /**
     * Transfered size in bytes.
     */
    long getTransferedSize();
}
