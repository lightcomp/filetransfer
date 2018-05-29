package com.lightcomp.ft.client.internal.operations;

import com.lightcomp.ft.client.internal.ExceptionType;
import com.lightcomp.ft.client.internal.operations.OperationResult.Type;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.wsdl.v1.FileTransferService;
import com.lightcomp.ft.xsd.v1.FileTransferState;
import com.lightcomp.ft.xsd.v1.FinishRequest;
import com.lightcomp.ft.xsd.v1.GenericDataType;
import com.lightcomp.ft.xsd.v1.TransferStatus;

public class FinishOperation extends RecoverableOperation {

    private FinishResult result;

    public FinishOperation(OperationHandler handler, FileTransferService service) {
        super(handler, service);
    }

    @Override
    public FinishResult execute() {
        executeInternal();
        return result;
    }

    @Override
    protected void send() throws FileTransferException {
        FinishRequest fr = new FinishRequest();
        fr.setTransferId(getTransferId());
        GenericDataType resp = service.finish(fr);
        result = new FinishResult(Type.SUCCESS, resp);
    }

    @Override
    protected boolean prepareResend(TransferStatus status) {
        FileTransferState fts = status.getState();
        if (fts == FileTransferState.ACTIVE) {
            return true;
        }
        if (fts == FileTransferState.FINISHED) {
            result = new FinishResult(Type.SUCCESS, status.getResp());
            return false; // success
        }
        ErrorDesc ed = new ErrorDesc("Finish operation failed, invalid server state").addParam("serverState", fts);
        result = new FinishResult(Type.FAIL, ed);
        return false; // fail
    }

    @Override
    protected void recoveryFailed(Type reason, Throwable src, ExceptionType srcType) {
        ErrorDesc ed = new ErrorDesc("Finish operation failed").setCause(src).setCauseType(srcType);
        result = new FinishResult(reason, ed);
    }
}