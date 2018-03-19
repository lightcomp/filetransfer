package com.lightcomp.ft.receiver;

import java.nio.file.Path;

/**
 * Acceptor for incoming file transfer.
 */
public interface TransferAcceptor {

    /**
     * Unique transfer id.
     */
    String getTransferId();

    /**
     * Target directory for transfered items.
     */
    Path getTransferDir();

    /**
     * Transfer progress callback.
     */
    void onTransferProgress(TransferStatus status);

    /**
     * Transfer success callback.
     */
    void onTransferSuccess();

    /**
     * Transfer aborted callback.
     */
    void onTransferAborted();

    /**
     * Transfer failed callback.
     */
    void onTransferFailed(Throwable cause);
}
