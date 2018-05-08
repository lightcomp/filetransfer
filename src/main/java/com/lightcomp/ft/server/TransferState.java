package com.lightcomp.ft.server;

import com.lightcomp.ft.xsd.v1.FileTransferState;

/**
 * Internal receiver transfer state. <i>Implementation note: Do not change
 * defined order, ordinal is used.</i>
 */
public enum TransferState {
    /**
     * Implicit state after creation.
     */
    INITIALIZED(null),
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
     * File transfer is failed.
     */
    FAILED(FileTransferState.FAILED),
    /**
     * File transfer is canceled.
     */
    CANCELED(FileTransferState.FAILED);

    private final FileTransferState external;

    private TransferState(FileTransferState external) {
        this.external = external;
    }

    public boolean isTerminal() {
        return ordinal() >= FINISHED.ordinal();
    }

    public FileTransferState toExternal() {
        return external;
    }
}