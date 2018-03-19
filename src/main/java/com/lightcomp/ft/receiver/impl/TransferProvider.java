package com.lightcomp.ft.receiver.impl;

import com.lightcomp.ft.common.ChecksumType;

import cxf.FileTransferException;

public interface TransferProvider {

    TransferImpl createTransfer(String requestId, ChecksumType checksumType) throws FileTransferException;

    TransferImpl getTransfer(String transferId) throws FileTransferException;
}
