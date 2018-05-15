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
     * Recovery count of communication with server.
     */
    int getRecoveryCount();

    /**
     * Transfered size in bytes.
     */
    long getTransferedSize();
}
