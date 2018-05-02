package com.lightcomp.ft.server;

/**
 * File transfer acceptor initialized by {@link TransferReceiver}.
 */
public interface TransferAcceptor {

    public enum Mode {
        UPLOAD, DOWNLOAD
    }

    /**
     * Unique transfer id, not-null.
     */
    String getTransferId();

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
    void onTransferSuccess();

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
