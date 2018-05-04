package com.lightcomp.ft.client;

public interface TransferRequest {

    /**
     * Request id.
     */
    String getRequestId();

    /**
     * Transfer begin callback.
     */
    void onTransferBegin(Transfer transfer);

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
