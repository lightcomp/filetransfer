package com.lightcomp.ft.client;

import com.lightcomp.ft.xsd.v1.GenericDataType;

/**
 * File transfer request.
 */
public interface TransferRequest {

    /**
     * Request data, not-null.
     */
    GenericDataType getData();

    /**
     * Request id which is used only for logging, when null data id is used instead.
     */
    String getLogId();

    /**
     * Transfer begin callback.
     */
    void onTransferInitialized(Transfer transfer);

    /**
     * Transfer progress callback.
     */
    void onTransferProgress(TransferStatus status);

    /**
     * Transfer canceled callback.
     */
    void onTransferCanceled();

    /**
     * Transfer failed callback.
     */
    void onTransferFailed(Throwable cause);

    /**
     * Transfer success callback.
     */
    void onTransferSuccess(GenericDataType response);
}
