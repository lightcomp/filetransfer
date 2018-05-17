package com.lightcomp.ft.server.internal;

import com.lightcomp.ft.server.TransferStatus;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.xsd.v1.GenericDataType;

public interface TransferManager {

    /**
     * Creates transfer.
     * 
     * Transfer is created asynchronously.
     * 
     * @return transfer id
     */
    String createTransferAsync(GenericDataType request) throws FileTransferException;

    /**
     * @throws FileTransferException
     *             <ul>
     *             <li>busy code when transfer not yet created</li>
     *             <li>fatal code when transfer not found or server is not running</li>
     *             </ul>
     */
    Transfer getTransfer(String transferId) throws FileTransferException;

    /**
     * @throws FileTransferException
     *             <ul>
     *             <li>busy code when transfer is busy or not yet created</li>
     *             <li>fatal code when transfer status not found</li>
     *             </ul>
     */
    TransferStatus getConfirmedStatus(String transferId) throws FileTransferException;
}
