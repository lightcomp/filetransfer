package com.lightcomp.ft.client.operations;

import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.wsdl.v1.FileTransferService;
import com.lightcomp.ft.xsd.v1.FileTransferState;
import com.lightcomp.ft.xsd.v1.FileTransferStatus;
import com.lightcomp.ft.xsd.v1.GenericData;

public class FinishOperation extends RecoverableOperation {

    private GenericData response;

    public FinishOperation(String transferId, OperationHandler handler) {
        super(transferId, handler);
    }

    public GenericData getResponse() {
        return response;
    }

    @Override
    public boolean isInterruptible() {
        return false;
    }

    @Override
    public TransferExceptionBuilder prepareException(Throwable t) {
        return TransferExceptionBuilder.from("Failed to finish transfer").setCause(t);
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
        throw TransferExceptionBuilder.from("Invalid server state").addParam("name", fts).build();
    }

    @Override
    protected void send(FileTransferService service) throws FileTransferException {
        response = service.finish(transferId);
    }
}