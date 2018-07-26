package com.lightcomp.ft.client;

/**
 * File transfer asynchronous client, dispatching requests to server.
 */
public interface Client {

	/**
	 * Begin asynchronous upload. Client must be started first.
	 * 
	 * @return Instance of transfer.
	 */
	Transfer upload(UploadRequest request);

	/**
	 * Begin synchronous upload. Client doesn't have to be started first.
	 * 
	 * @return Instance of transfer.
	 */
	void uploadSync(UploadRequest request);
	
	/**
	 * Begin asynchronous download. Client must be started first.
	 * 
	 * @return Instance of transfer.
	 */
	Transfer download(DownloadRequest request);

	/**
	 * Begin synchronous download. Client doesn't have to be started first.
	 * 
	 * @return Instance of transfer.
	 */
	void downloadSync(DownloadRequest request);
	
	/**
	 * Starts asynchronous request processing.
	 */
	void start();

	/**
	 * Stops asynchronous request processing.
	 */
	void stop();
}
