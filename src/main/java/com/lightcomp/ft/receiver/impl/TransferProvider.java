package com.lightcomp.ft.receiver.impl;

import com.lightcomp.ft.common.ChecksumType;

import cxf.FileTransferException;

public interface TransferProvider {

    Transfer createTransfer(String requestId, ChecksumType checksumType) throws FileTransferException;

    Transfer getTransfer(String transferId) throws FileTransferException;
}
