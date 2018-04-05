package com.lightcomp.ft.server.impl;

import cxf.FileTransferException;

public interface TransferProvider {

    TransferImpl createTransfer(String requestId) throws FileTransferException;

    TransferImpl getTransfer(String transferId) throws FileTransferException;
}
