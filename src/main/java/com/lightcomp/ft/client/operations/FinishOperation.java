package com.lightcomp.ft.client.operations;

import com.lightcomp.ft.core.TransferInfo;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.wsdl.v1.FileTransferService;
import com.lightcomp.ft.xsd.v1.FileTransferState;
import com.lightcomp.ft.xsd.v1.FileTransferStatus;
import com.lightcomp.ft.xsd.v1.GenericData;

public class FinishOperation extends RecoverableOperation {

    private GenericData response;

    public FinishOperation(TransferInfo transfer, RecoveryHandler handler) {
        super(transfer, handler);
    }

    public GenericData getResponse() {
        return response;
    }

    @Override
    public boolean isInterruptible() {
        return recoveryCount == 0;
    }

    @Override
    protected TransferExceptionBuilder prepareException(Throwable cause) {
        return TransferExceptionBuilder.from("Failed to finish transfer", transfer).setCause(cause);
    }

    @Override
    protected void send(FileTransferService service) throws FileTransferException {
        response = service.finish(transfer.getTransferId());
    }

    @Override
    protected boolean isFinished(FileTransferStatus status) {
        FileTransferState fts = status.getState();
        if (fts == FileTransferState.ACTIVE) {
            return false; // next try
        }
        if (fts == FileTransferState.FINISHED) {
            response = status.getResp();
            return true; // success
        }
        throw new IllegalStateException("Invalid server state, name=" + fts);
    }
}