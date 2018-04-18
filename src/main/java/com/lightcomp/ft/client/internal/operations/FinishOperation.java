package com.lightcomp.ft.client.internal.operations;

import com.lightcomp.ft.core.TransferInfo;
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.xsd.v1.FileTransferState;
import com.lightcomp.ft.xsd.v1.FileTransferStatus;

import cxf.FileTransferException;
import cxf.FileTransferService;

public class FinishOperation extends RecoverableOperation {

    public FinishOperation(TransferInfo transferInfo, RecoveryHandler handler) {
        super(transferInfo, handler);
    }

    @Override
    protected void send(FileTransferService service) throws FileTransferException {
        service.finish(transferInfo.getTransferId());
    }

    @Override
    protected TransferException createException(Throwable cause) {
        return TransferExceptionBuilder.from(transferInfo, "Failed to finish transfer").setCause(cause).build();
    }

    @Override
    protected boolean isFinished(FileTransferStatus status) {
        FileTransferState fts = status.getState();
        if (fts == FileTransferState.ACTIVE) {
            return false; // next try
        }
        if (fts == FileTransferState.FINISHED) {
            return true; // success
        }
        throw new IllegalStateException("Invalid server state, name=" + fts);
    }
}