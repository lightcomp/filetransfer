package com.lightcomp.ft.client.internal.operations;

import com.lightcomp.ft.client.internal.ExceptionType;
import com.lightcomp.ft.client.internal.operations.OperationStatus.Type;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.wsdl.v1.FileTransferService;
import com.lightcomp.ft.xsd.v1.TransferStatus;
import com.lightcomp.ft.xsd.v1.TransferStatusRequest;

public abstract class AbstractOperation {

    protected final OperationHandler handler;

    protected final FileTransferService service;

    protected AbstractOperation(OperationHandler handler, FileTransferService service) {
        this.handler = handler;
        this.service = service;
    }

    public OperationStatus execute() {
        boolean recovery = false;
        while (true) {
            try {
                if (recovery) {
                    TransferStatus ss = sendServerStatus();
                    OperationStatus status = resolveServerStatus(ss);
                    if (status != null) {
                        return status;
                    }
                    // null -> we can continue (resend)
                }
                send();
                break;
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
        if (recovery) {
            handler.recoverySucceeded();
        }
        return operationFinished();
    }

    protected abstract void send() throws FileTransferException;

    protected boolean isRecoverable(ExceptionType type) {
        return type == ExceptionType.BUSY || type == ExceptionType.CONNECTION;
    }

    /**
     * @return Returns operation status or null when operation wasn't executed.
     */
    protected abstract OperationStatus resolveServerStatus(TransferStatus status);

    /**
     * @return Returns operation status. Can be fail when result didn't pass validation.
     */
    protected OperationStatus operationFinished() {
        return new OperationStatus(Type.SUCCESS);
    }

    /**
     * @return Returns operation status which represents recovery failure.
     */
    protected OperationStatus recoveryFailed(Type type, Throwable ex, ExceptionType exType) {
        return new OperationStatus(type).setFailureCause(ex).setFailureType(exType);
    }

    private TransferStatus sendServerStatus() throws FileTransferException {
        TransferStatusRequest tsr = new TransferStatusRequest();
        tsr.setTransferId(handler.getTransferId());
        return service.status(tsr);
    }
}
