package com.lightcomp.ft.sender.impl.phase.operation;

import com.lightcomp.ft.exception.CanceledException;
import com.lightcomp.ft.sender.impl.phase.RecoverablePhase;
import com.lightcomp.ft.xsd.v1.FileTransferStatus;

import cxf.FileTransferException;
import cxf.FileTransferService;

public abstract class RetryOperation implements Operation {

    private final RecoverablePhase phase;

    private final Operation operation;

    public RetryOperation(RecoverablePhase phase, Operation operation) {
        this.phase = phase;
        this.operation = operation;
    }

    @Override
    public final void send() throws FileTransferException, CanceledException {
        FileTransferService service = phase.getService();
        String transferId = phase.getTransferInfo().getTransferId();

        // try to get current status
        FileTransferStatus status = service.getStatus(transferId);

        // if success then resend original operation
        if (canContinue(status)) {
            operation.send();
        }
    }

    @Override
    public final Operation createRetry() {
        return this;
    }

    protected abstract boolean canContinue(FileTransferStatus status);
}
