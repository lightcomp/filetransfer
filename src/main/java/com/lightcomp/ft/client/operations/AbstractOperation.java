package com.lightcomp.ft.client.operations;

import com.lightcomp.ft.client.internal.ExceptionType;
import com.lightcomp.ft.client.operations.OperationStatus.Type;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.wsdl.v1.FileTransferService;
import com.lightcomp.ft.xsd.v1.TransferStatus;
import com.lightcomp.ft.xsd.v1.TransferStatusRequest;

public abstract class AbstractOperation {

    protected final OperationHandler handler;

    protected final FileTransferService service;

    protected boolean recovery;

    protected AbstractOperation(OperationHandler handler, FileTransferService service) {
        this.handler = handler;
        this.service = service;
    }

    public OperationStatus execute() {
        while (true) {
            try {
                if (recovery) {
                    TransferStatus ss = sendServerStatus();
                    OperationStatus status = resolveServerStatus(ss);
                    if (status != null) {
                        return status;
                    }
                }
                send();
                return new OperationStatus(Type.SUCCESS, recovery);
            } catch (Throwable t) {
                ExceptionType type = ExceptionType.resolve(t);
                if (!isRecoverable(type)) {
                    return recoveryFailed(Type.FAIL, t, type);
                }
                if (!handler.prepareRecovery()) {
                    return recoveryFailed(Type.CANCEL, t, type);
                }
                recovery = true;
            }
        }
    }

    /**
     * @return Operation status when execute should terminate or null if recovery can continue.
     */
    protected abstract OperationStatus resolveServerStatus(TransferStatus ss);

    protected abstract void send() throws FileTransferException;

    /**
     * @return Operation status which represents recovery failure.
     */
    protected OperationStatus recoveryFailed(Type type, Throwable ex, ExceptionType exType) {
        return new OperationStatus(type, recovery).setFailureCause(ex).setFailureType(exType);
    }

    protected boolean isRecoverable(ExceptionType type) {
        return type == ExceptionType.BUSY || type == ExceptionType.CONNECTION;
    }

    private TransferStatus sendServerStatus() throws FileTransferException {
        TransferStatusRequest tsr = new TransferStatusRequest();
        tsr.setTransferId(handler.getTransferId());
        return service.status(tsr);
    }
}
