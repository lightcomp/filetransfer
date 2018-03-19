package com.lightcomp.ft.sender;

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
     * Transfer start time.
     */
    LocalDateTime getStartTime();

    /**
     * Time of last successful transfer activity.
     */
    LocalDateTime getLastTransferTime();

    /**
     * Last transfer operation recovery count.
     */
    int getRecoveryCount();

    /**
     * Total size of transfer (bytes).
     */
    long getTotalTransferSize();

    /**
     * Transfered size (bytes).
     */
    long getTransferedSize();
}
