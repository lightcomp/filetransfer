package com.lightcomp.ft.client;

/**
 * Client dispatching all transfers.
 */
public interface Client {

    /**
     * Begin async upload.
     * 
     * @return Instance of transfer.
     */
    Transfer beginUpload(UploadRequest request);

    /**
     * Begin async download.
     * 
     * @return Instance of transfer.
     */
    Transfer beginDownload(DownloadRequest request);

    /**
     * Start client.
     */
    void start();

    /**
     * Stop client.
     */
    void stop();
}
