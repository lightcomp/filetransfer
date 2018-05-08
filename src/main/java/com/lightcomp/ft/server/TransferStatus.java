package com.lightcomp.ft.server;

import java.time.LocalDateTime;

import com.lightcomp.ft.xsd.v1.GenericData;

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
     * Server response to finished transfer.
     */
    GenericData getResponse();

    /**
     * Cause of transfer failure.
     */
    Throwable getFailureCause();
}
