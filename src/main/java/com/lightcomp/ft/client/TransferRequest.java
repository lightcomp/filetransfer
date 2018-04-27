package com.lightcomp.ft.client;

import com.lightcomp.ft.exception.TransferException;

public interface TransferRequest {

    /**
     * Request id.
     */
    String getRequestId();

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
    void onTransferFailed(TransferException cause);
}
