package com.lightcomp.ft.server;

import com.lightcomp.ft.xsd.v1.GenericDataType;

/**
 * Transfer data handler, initialized by {@link TransferHandler}. General interface which shouldn't be implemented directly,
 * specialization of {@link UploadHandler} or {@link DownloadHandler} is expected.
 */
public interface TransferDataHandler {

    public enum Mode {
        UPLOAD, DOWNLOAD
    }

    /**
     * Transfer mode, not-null. Based on the mode this class must implement {@link UploadHandler} or {@link DownloadHandler}
     * specialization.
     */
    Mode getMode();

    /**
     * Request id which is used only for logging.
     */
    String getRequestId();

    /**
     * Transfer progress callback.
     */
    void onTransferProgress(TransferStatus status);

    /**
     * Transfer success callback.
     */
    GenericDataType onTransferSuccess();

    /**
     * Transfer canceled callback.
     */
    void onTransferCanceled();

    /**
     * Transfer failed callback.
     */
    void onTransferFailed(ErrorDesc errorDesc);
}
