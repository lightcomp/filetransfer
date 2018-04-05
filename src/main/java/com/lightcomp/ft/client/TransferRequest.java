package com.lightcomp.ft.client;

import com.lightcomp.ft.exception.TransferException;

/**
 * Transfer request supplies source data and implements transfer callbacks.
 */
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
