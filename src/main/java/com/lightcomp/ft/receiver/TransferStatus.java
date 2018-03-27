package com.lightcomp.ft.receiver;

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
     * Transfer start time.
     */
    LocalDateTime getStartTime();

    /**
     * Time of last successful transfer or state change.
     */
    LocalDateTime getLastActivity();

    /**
     * Total size of transfer (bytes).
     */
    long getTotalTransferSize();

    /**
     * Transfered size (bytes).
     */
    long getTransferedSize();
}
