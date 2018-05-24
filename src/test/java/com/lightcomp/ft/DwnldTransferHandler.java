package com.lightcomp.ft;

import com.lightcomp.ft.server.DownloadHandler;
import com.lightcomp.ft.server.TransferDataHandler;
import com.lightcomp.ft.server.TransferHandler;
import com.lightcomp.ft.xsd.v1.GenericDataType;

public abstract class DwnldTransferHandler implements TransferHandler {

    private String lastTransferId;

    @Override
    public synchronized TransferDataHandler onTransferBegin(String transferId, GenericDataType request) {
        lastTransferId = transferId;
        return createDownload(transferId, request);
    }

    public synchronized String getLastTransferId() {
        return lastTransferId;
    }

    protected abstract DownloadHandler createDownload(String transferId, GenericDataType request);
}
