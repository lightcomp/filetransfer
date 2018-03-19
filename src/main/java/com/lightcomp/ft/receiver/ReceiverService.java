package com.lightcomp.ft.receiver;

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
     * Abort transfer. When async transfer aborted
     * {@link TransferAcceptor#onTransferAborted()} is called.
     */
    void cancelTransfer(String transferId);
}