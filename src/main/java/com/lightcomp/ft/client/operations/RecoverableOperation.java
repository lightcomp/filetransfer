package com.lightcomp.ft.client.operations;

import com.lightcomp.ft.client.internal.ExceptionType;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.wsdl.v1.FileTransferService;
import com.lightcomp.ft.xsd.v1.FileTransferStatus;

public abstract class RecoverableOperation {

    protected final String transferId;

    protected final OperationHandler handler;

    private boolean recovery;

    protected RecoverableOperation(String transferId, OperationHandler handler) {
        this.transferId = transferId;
        this.handler = handler;
    }

    public boolean isInterruptible() {
        return true;
    }

    public boolean execute(FileTransferService service) {
        while (true) {
            try {
                sendInternal(service);
                if (recovery) {
                    handler.recoverySucceeded();
                }
                return true;
            } catch (Throwable t) {
                if (!handleException(t)) {
                    return false;
                }
                recovery = true;
            }
        }
    }

    protected abstract TransferExceptionBuilder prepareException(Throwable t);

    protected abstract boolean isFinished(FileTransferStatus status);

    protected abstract void send(FileTransferService service) throws FileTransferException;

    private void sendInternal(FileTransferService service) throws FileTransferException {
        if (recovery) {
            // try to get current server status
            FileTransferStatus status = service.status(transferId);
            // if succeeded test server status
            if (isFinished(status)) {
                return;
            }
        }
        send(service);
    }

    private boolean handleException(Throwable t) {
        ExceptionType type = ExceptionType.resolve(t);
        // fail transfer if not busy or connection type
        if (type != ExceptionType.BUSY && type != ExceptionType.CONNECTION) {
            throw prepareException(t).build();
        }
        if (!handler.prepareRecovery(isInterruptible())) {
            return false;
        }
        return true;
    }
}
