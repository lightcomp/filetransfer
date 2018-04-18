package com.lightcomp.ft.server;

import com.lightcomp.ft.exception.TransferException;

/**
 * Receiver service dispatching all transfer requests.
 */
public interface Server {

    /**
     * Returns WS API implementor, suitable for end-point publishing.
     */
    Object getImplementor();

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
     * When canceled {@link TransferAcceptor#onTransferCanceled()} is called.
     * 
     * 
     * @throws TransferException
     *             When transfer does not exist or already finished/failed.
     */
    void cancelTransfer(String transferId);
}