package com.lightcomp.ft.client.operations;

import com.lightcomp.ft.core.TransferInfo;
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.wsdl.v1.FileTransferService;
import com.lightcomp.ft.xsd.v1.FileTransferStatus;

public abstract class RecoverableOperation implements Operation {

    protected final OperationListener listener;

    protected final TransferInfo transfer;

    protected int recoveryCount;

    protected RecoverableOperation(OperationListener listener, TransferInfo transfer) {
        this.listener = listener;
        this.transfer = transfer;
    }

    @Override
    public int getRecoveryCount() {
        return recoveryCount;
    }

    @Override
    public boolean execute(FileTransferService service) {
        while (true) {
            if (!listener.isOperationFeasible(this)) {
                return false;
            }
            try {
                executeInternal(service);
                return true;
            } catch (Throwable t) {
                if (!isRecoverableException(t)) {
                    throw prepareException(t);
                }
                recoveryCount++;
            }
        }
    }

    protected abstract void send(FileTransferService service) throws FileTransferException;

    protected abstract boolean isFinished(FileTransferStatus status);

    protected abstract TransferException prepareException(Throwable cause);

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
