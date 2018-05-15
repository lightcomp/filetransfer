package com.lightcomp.ft.client;

/**
 * File transfer client, dispatching requests to server.
 */
public interface Client {

    /**
     * Begin async upload.
     * 
     * @return Instance of transfer.
     */
    Transfer upload(UploadRequest request);

    /**
     * Begin async download.
     * 
     * @return Instance of transfer.
     */
    Transfer download(DownloadRequest request);

    /**
     * Start client.
     */
    void start();

    /**
     * Stop client.
     */
    void stop();
}
