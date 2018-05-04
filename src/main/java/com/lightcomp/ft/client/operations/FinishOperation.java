package com.lightcomp.ft.client.operations;

import com.lightcomp.ft.core.TransferInfo;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.wsdl.v1.FileTransferService;
import com.lightcomp.ft.xsd.v1.FileTransferState;
import com.lightcomp.ft.xsd.v1.FileTransferStatus;

public class FinishOperation extends RecoverableOperation {

    public FinishOperation(TransferInfo transfer, RecoveryHandler handler) {
        super(transfer, handler);
    }

    @Override
    public boolean isInterruptible() {
        return recoveryCount == 0;
    }

    @Override
    public TransferExceptionBuilder prepareException(Throwable cause) {
        return TransferExceptionBuilder.from("Failed to finish transfer", transfer).setCause(cause);
    }

    @Override
    protected void send(FileTransferService service) throws FileTransferException {
        service.finish(transfer.getTransferId());
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