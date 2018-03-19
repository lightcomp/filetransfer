package com.lightcomp.ft.sender.impl.phase;

import com.lightcomp.ft.exception.CanceledException;
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.sender.TransferState;
import com.lightcomp.ft.sender.impl.TransferContext;
import com.lightcomp.ft.sender.impl.phase.operation.CommitOperation;
import com.lightcomp.ft.sender.impl.phase.operation.Operation;

public class CommitPhase extends RecoverablePhase {

    private boolean opCreated;

    protected CommitPhase(TransferContext transferCtx) {
        super(transferCtx);
    }

    @Override
    public Phase getNextPhase() {
        return null;
    }

    @Override
    public TransferState getNextState() {
        return TransferState.COMMITTED;
    }

    @Override
    protected Operation getNextOperation() throws CanceledException {
        if (opCreated) {
            return null;
        }
        opCreated = true;
        return new CommitOperation(this);
    }

    @Override
    protected TransferException createTransferException(OperationError error) {
        return TransferExceptionBuilder.from("Failed to commit transfer").setTransfer(transferCtx).setCause(error.getCause()).build();
    }
}
