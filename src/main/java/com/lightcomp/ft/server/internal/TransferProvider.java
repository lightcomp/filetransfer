package com.lightcomp.ft.server.internal;

import cxf.FileTransferException;

public interface TransferProvider {

    Transfer createTransfer(String requestId) throws FileTransferException;

    Transfer getTransfer(String transferId) throws FileTransferException;
}
