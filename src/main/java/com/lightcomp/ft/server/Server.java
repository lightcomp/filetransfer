package com.lightcomp.ft.server;

import com.lightcomp.ft.exception.TransferException;

/**
 * File transfer server.
 */
public interface Server {

    EndpointFactory getEndpointFactory();

    /**
     * Start service.
     */
    void start();

    /**
     * Stop service.
     */
    void stop();

    /**
     * Cancel transfer. Caller will wait for termination if the transfer is active.
     * When canceled {@link TransferDataHandler#onTransferCanceled()} is called.
     * 
     * 
     * @throws TransferException
     *             When transfer does not exist or already finished/failed.
     */
    void cancelTransfer(String transferId) throws TransferException;

    /**
     * @return Returns current transfer status or null when not found.
     */
    TransferStatus getTransferStatus(String transferId);
}