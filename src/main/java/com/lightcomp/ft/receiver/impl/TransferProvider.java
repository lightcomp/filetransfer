package com.lightcomp.ft.receiver.impl;

import cxf.FileTransferException;

public interface TransferProvider {

    TransferImpl createTransfer(String requestId) throws FileTransferException;

    TransferImpl getTransfer(String transferId) throws FileTransferException;
}
