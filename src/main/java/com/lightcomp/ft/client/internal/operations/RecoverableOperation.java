package com.lightcomp.ft.client.internal.operations;

import com.lightcomp.ft.client.internal.ExceptionType;
import com.lightcomp.ft.client.internal.operations.OperationResult.Type;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.wsdl.v1.FileTransferService;
import com.lightcomp.ft.xsd.v1.TransferStatus;
import com.lightcomp.ft.xsd.v1.TransferStatusRequest;

public abstract class RecoverableOperation {

    private final OperationHandler handler;

    protected final FileTransferService service;

    protected RecoverableOperation(OperationHandler handler, FileTransferService service) {
        this.handler = handler;
        this.service = service;
    }

    public String getTransferId() {
        return handler.getTransferId();
    }

    public abstract OperationResult execute();

    protected final void executeInternal() {
        boolean recovery = false;
        while (true) {
            try {
                if (recovery) {
                    TransferStatus status = sendServerStatus();
                    if (!prepareResend(status)) {
                        return; // fail or success
                    }
                }
                send();
                return; // fail (response validation) or success
            } catch (Throwable t) {
                ExceptionType type = ExceptionType.resolve(t);
                if (!isRecoverable(type)) {
                    recoveryFailed(Type.FAIL, t, type);
                    return; // fail
                }
                if (!handler.prepareRecovery()) {
                    recoveryFailed(Type.CANCEL, t, type);
                    return; // fail
                }
                recovery = true;
            }
        }
    }

    protected abstract void send() throws Throwable;

    protected abstract boolean prepareResend(TransferStatus status);

    protected abstract void recoveryFailed(Type reason, Throwable src, ExceptionType srcType);

    private boolean isRecoverable(ExceptionType type) {
        return type == ExceptionType.BUSY || type == ExceptionType.CONNECTION;
    }

    private TransferStatus sendServerStatus() throws FileTransferException {
        TransferStatusRequest tsr = new TransferStatusRequest();
        tsr.setTransferId(handler.getTransferId());
        return service.status(tsr);
    }
}
