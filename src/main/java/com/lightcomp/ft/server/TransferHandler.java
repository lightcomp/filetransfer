package com.lightcomp.ft.server;

import com.lightcomp.ft.xsd.v1.GenericData;

/**
 * Handler for upload transfer.
 */
public interface TransferHandler {

    /**
     * Creates data handler for transfer request.
     * 
     * @param transferId
     *            generated transfer id, not-null
     * @param request
     *            client request
     * @return Returns data handler or null when rejected.
     */
    TransferDataHandler onTransferBegin(String transferId, GenericData request);
}
