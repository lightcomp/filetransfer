package com.lightcomp.ft.server.internal;

import com.lightcomp.ft.wsdl.v1.FileTransferException;

public interface TransferProvider {

    Transfer createTransfer(String requestId) throws FileTransferException;

    Transfer getTransfer(String transferId) throws FileTransferException;
}
