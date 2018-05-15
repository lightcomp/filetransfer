package com.lightcomp.ft.server;

import com.lightcomp.ft.xsd.v1.FileTransferState;

/**
 * Internal transfer state. <i>Implementation note: Do not change defined order, ordinal is used.</i>
 */
public enum TransferState {
    /**
     * Implicit state after creation.
     */
    CREATED(null),
    /**
     * Data is transferring.
     */
    STARTED(FileTransferState.ACTIVE),
    /**
     * Data is transfered.
     */
    TRANSFERED(FileTransferState.ACTIVE),
    /**
     * Data is transfered, transfer waiting for response from acceptor.
     */
    FINISHING(null),
    /**
     * Transfer is finished.
     */
    FINISHED(FileTransferState.FINISHED),
    /**
     * Transfer is failed.
     */
    FAILED(FileTransferState.FAILED),
    /**
     * Transfer is canceled.
     */
    CANCELED(FileTransferState.FAILED);

    private final FileTransferState external;

    private TransferState(FileTransferState external) {
        this.external = external;
    }

    /**
     * @return Returns true for FINISHED, FAILED and CANCELED states.
     */
    public boolean isTerminal() {
        return ordinal() >= FINISHED.ordinal();
    }

    /**
     * Converts internal state to external state for API communication.
     * 
     * @return External state or null when internal state is not possible to convert.
     */
    public FileTransferState toExternal() {
        return external;
    }
}