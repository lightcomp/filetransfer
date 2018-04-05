package com.lightcomp.ft.server;

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
     * Transfer canceled callback.
     */
    void onTransferCanceled();

    /**
     * Transfer failed callback.
     */
    void onTransferFailed(Throwable cause);
}
