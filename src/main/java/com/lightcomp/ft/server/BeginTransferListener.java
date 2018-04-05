package com.lightcomp.ft.server;

/**
 * Listener which will be notified about any transfer request.
 */
public interface BeginTransferListener {

    /**
     * Creates acceptor for transfer request.
     * 
     * @param requestId
     *            external request id
     * @return Returns acceptor for incoming transfer.
     * @throws RuntimeException
     *             If transfer can't be processed.
     */
    TransferAcceptor onTransferBegin(String requestId);
}
