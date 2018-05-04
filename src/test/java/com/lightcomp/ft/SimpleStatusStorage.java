package com.lightcomp.ft;

import java.util.HashMap;
import java.util.Map;

import com.lightcomp.ft.server.TransferStatus;
import com.lightcomp.ft.server.TransferStatusStorage;

public class SimpleStatusStorage implements TransferStatusStorage {

    private final Map<String, TransferStatus> transferIdMap = new HashMap<>();

    @Override
    public synchronized void saveTransferStatus(String transferId, TransferStatus status) {
        transferIdMap.putIfAbsent(transferId, status);
    }

    @Override
    public synchronized TransferStatus getTransferStatus(String transferId) {
        return transferIdMap.get(transferId);
    }
}
