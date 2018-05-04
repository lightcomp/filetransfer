package com.lightcomp.ft.server.internal;

import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.xsd.v1.FileTransferStatus;

public interface TransferManager {

    FileTransferStatus getFileTransferStatus(String transferId) throws FileTransferException;

    Transfer createTransfer(String requestId) throws FileTransferException;

    Transfer getTransfer(String transferId) throws FileTransferException;
}
