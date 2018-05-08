package com.lightcomp.ft.client;

import com.lightcomp.ft.xsd.v1.GenericData;

public interface TransferRequest {

    /**
     * Request data, not-null.
     */
    GenericData getData();

    /**
     * Request id which is used only for logging. Data id is used when value is null.
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
    void onTransferSuccess(GenericData response);
}
