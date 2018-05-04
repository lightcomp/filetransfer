package com.lightcomp.ft.client.operations;

import com.lightcomp.ft.core.TransferInfo;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.wsdl.v1.FileTransferService;
import com.lightcomp.ft.xsd.v1.FileTransferStatus;

public abstract class RecoverableOperation {

    protected final TransferInfo transfer;

    protected final RecoveryHandler handler;

    protected int recoveryCount;

    protected RecoverableOperation(TransferInfo transfer, RecoveryHandler handler) {
        this.transfer = transfer;
        this.handler = handler;
    }

    public abstract boolean isInterruptible();

    public boolean execute(FileTransferService service) {
        while (true) {
            try {
                executeInternal(service);
                // report success to recovery handler
                if (recoveryCount > 0) {
                    handler.onRecoverySuccess();
                }
                return true;
            } catch (Throwable t) {
                recoveryCount++;
                // check if exception is recoverable
                if (!isRecoverableException(t)) {
                    throw prepareException(t).addParam("recoveryCount", recoveryCount).build();
                }
                // prepare recovery
                if (!handler.prepareRecovery(isInterruptible())) {
                    return false;
                }
            }
        }
    }

    protected abstract void send(FileTransferService service) throws FileTransferException;

    protected abstract boolean isFinished(FileTransferStatus status);

    protected abstract TransferExceptionBuilder prepareException(Throwable cause);

    private void executeInternal(FileTransferService service) throws FileTransferException {
        if (recoveryCount > 0) {
            // try to get current server status
            FileTransferStatus status = service.status(transfer.getTransferId());
            // if succeeded test server status
            if (isFinished(status)) {
                return;
            }
        }
        send(service);
    }

    private static boolean isRecoverableException(Throwable t) {
        ExceptionType type = ExceptionType.resolve(t);
        return type == ExceptionType.CONNECTION || type == ExceptionType.BUSY;
    }
}
