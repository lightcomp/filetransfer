package com.lightcomp.ft.server;

/**
 * Receiver will be notified about any transfer request.
 */
public interface TransferReceiver {

    /**
     * Creates acceptor for upload request.
     * 
     * @param requestId
     *            external request id
     * @return Returns acceptor for incoming transfer or null when rejected.
     */
    TransferAcceptor onTransferBegin(String requestId);
}
