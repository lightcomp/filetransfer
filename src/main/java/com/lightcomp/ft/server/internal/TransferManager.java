package com.lightcomp.ft.server.internal;

import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.xsd.v1.FileTransferStatus;
import com.lightcomp.ft.xsd.v1.GenericData;

public interface TransferManager {

    /**
     * Creates transfer.
     * 
     * Transfer is created asynchronously.
     * 
     * @return transfer id
     */
    String createTransferAsync(GenericData request) throws FileTransferException;

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
    FileTransferStatus getFileTransferStatus(String transferId) throws FileTransferException;
}
