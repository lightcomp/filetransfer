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
    INITIALIZED,
    /**
     * Data is transferring.
     */
    STARTED,
    /**
     * Data is transfered.
     */
    TRANSFERED,
    /**
     * Transfer is finished.
     */
    FINISHED,
    /**
     * File transfer is failed.
     */
    FAILED,
    /**
     * File transfer is canceled.
     */
    CANCELED;

    public static boolean isTerminal(TransferState state) {
        return state.ordinal() >= FINISHED.ordinal();
    }

    /**
     * Returns converted state or null when state cannot be converted e.g.
     * INITIALIZED.
     */
    public static FileTransferState convert(TransferState state) {
        switch (state) {
            case STARTED:
            case TRANSFERED:
                return FileTransferState.ACTIVE;
            case FINISHED:
                return FileTransferState.FINISHED;
            case FAILED:
            case CANCELED:
                return FileTransferState.FAILED;
            default:
                return null;
        }
    }
}