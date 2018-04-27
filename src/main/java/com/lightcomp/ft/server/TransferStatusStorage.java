package com.lightcomp.ft.server;

public interface TransferStatusStorage {

    void saveTransferStatus(String transferId, TransferStatus status);
    
    TransferStatus getTransferStatus(String transferId);
}
