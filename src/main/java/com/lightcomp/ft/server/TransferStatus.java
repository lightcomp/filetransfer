package com.lightcomp.ft.server;

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
     * Transfered size in bytes.
     */
    long getTransferedSize();

    /**
     * Sequential number of last processed frame.
     */
    int getLastFrameSeqNum();

    /**
     * Cause of transfer failure.
     */
    Throwable getFailureCause();
}
