package com.lightcomp.ft.client;

import java.time.LocalDateTime;

/**
 * File transfer status.
 */
public interface TransferStatus {

    /**
     * Transfer phase.
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
     * Last transfer operation recovery count.
     */
    int getRecoveryCount();

    /**
     * Transfered size in bytes.
     */
    long getTransferedSize();
}
