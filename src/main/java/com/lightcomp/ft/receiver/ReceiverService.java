package com.lightcomp.ft.receiver;

import com.lightcomp.ft.exception.TransferException;

/**
 * Receiver service dispatching all transfer requests.
 */
public interface ReceiverService {

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
     * Cancel transfer. Caller will wait if the transfer is processing commit. When
     * canceled {@link TransferAcceptor#onTransferCanceled()} is called.
     * 
     * @throws TransferException
     *             When transfer already committed.
     */
    void cancelTransfer(String transferId);
}