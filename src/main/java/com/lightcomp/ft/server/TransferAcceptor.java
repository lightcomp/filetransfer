package com.lightcomp.ft.server;

import com.lightcomp.ft.core.TransferInfo;
import com.lightcomp.ft.xsd.v1.GenericData;

/**
 * File transfer acceptor initialized by {@link TransferReceiver}.
 */
public interface TransferAcceptor extends TransferInfo {

    public enum Mode {
        UPLOAD, DOWNLOAD
    }

    /**
     * Transfer mode, not-null. Extending interface is expected to be implemented,
     * for UPLOAD mode interface {@link UploadAcceptor} and for DOWNLOAD mode
     * interface {@link DownloadAcceptor}.
     */
    Mode getMode();

    /**
     * Transfer progress callback.
     */
    void onTransferProgress(TransferStatus status);

    /**
     * Transfer success callback.
     */
    GenericData onTransferSuccess();

    /**
     * Transfer canceled callback.
     * 
     * @param aborted
     *            if true transfer was canceled by client
     */
    void onTransferCanceled();

    /**
     * Transfer failed callback.
     */
    void onTransferFailed(Throwable cause);
}
