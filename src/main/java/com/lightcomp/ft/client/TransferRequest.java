package com.lightcomp.ft.client;

import com.lightcomp.ft.xsd.v1.GenericData;

public interface TransferRequest {

    /**
     * Request data.
     */
    GenericData getData();

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
}
