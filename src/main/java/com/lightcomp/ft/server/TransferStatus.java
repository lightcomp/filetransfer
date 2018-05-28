package com.lightcomp.ft.server;

import java.time.LocalDateTime;

import com.lightcomp.ft.xsd.v1.GenericDataType;

/**
 * File transfer status.
 */
public interface TransferStatus {

    /**
     * Transfer state.
     */
    TransferState getState();

    /**
     * Last time of any transfer activity, used mainly for inactivity check. Does not necessary
     * represent last network activity.
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
     * Sequential number of last transfered frame.
     */
    int getTransferedSeqNum();

    /**
     * Sequential number of last processed frame.
     */
    int getProcessedSeqNum();

    /**
     * Server response after transfer is finished.
     */
    GenericDataType getResponse();

    /**
     * Error description of transfer failure.
     */
    ErrorDesc getErrorDesc();
}
