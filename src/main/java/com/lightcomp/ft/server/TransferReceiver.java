package com.lightcomp.ft.server;

import com.lightcomp.ft.xsd.v1.GenericData;

/**
 * Receiver will be notified about any transfer request.
 */
public interface TransferReceiver {

    /**
     * Creates acceptor for upload request.
     * 
     * @param request
     *            client request
     * @return Returns acceptor for incoming transfer or null when rejected.
     */
    TransferAcceptor onTransferBegin(GenericData request);
}
