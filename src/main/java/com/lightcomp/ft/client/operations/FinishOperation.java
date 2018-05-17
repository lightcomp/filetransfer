package com.lightcomp.ft.client.operations;

import com.lightcomp.ft.client.internal.ExceptionType;
import com.lightcomp.ft.client.operations.OperationStatus.Type;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.wsdl.v1.FileTransferService;
import com.lightcomp.ft.xsd.v1.FileTransferState;
import com.lightcomp.ft.xsd.v1.FinishRequest;
import com.lightcomp.ft.xsd.v1.GenericDataType;
import com.lightcomp.ft.xsd.v1.TransferStatus;

public class FinishOperation extends AbstractOperation {

    private GenericDataType response;

    public FinishOperation(OperationHandler handler, FileTransferService service) {
        super(handler, service);
    }

    public GenericDataType getResponse() {
        return response;
    }

    @Override
    protected void send() throws FileTransferException {
        FinishRequest fr = new FinishRequest();
        fr.setTransferId(handler.getTransferId());
        response = service.finish(fr);
    }

    @Override
    protected OperationStatus resolveServerStatus(TransferStatus status) {
        FileTransferState fts = status.getState();
        if (fts == FileTransferState.ACTIVE) {
            return null; // next try
        }
        if (fts == FileTransferState.FINISHED) {
            response = status.getResp();
            return new OperationStatus(Type.SUCCESS);
        }
        return new OperationStatus(Type.FAIL).setFailureMessage("Failed to finish transfer, invalid server state")
                .addFailureParam("serverState", fts);
    }

    @Override
    protected OperationStatus recoveryFailed(Type type, Throwable ex, ExceptionType exType) {
        return super.recoveryFailed(type, ex, exType).setFailureMessage("Failed to finish transfer");
    }
}