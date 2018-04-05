package com.lightcomp.ft.client.internal.operations;

import com.lightcomp.ft.client.internal.TransferContext;
import com.lightcomp.ft.exception.CanceledException;
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.exception.TransferExceptionBuilder;

import cxf.FileTransferException;

public class OperationDispatcher {

    protected final TransferContext transferCtx;

    public OperationDispatcher(TransferContext transferCtx) {
        this.transferCtx = transferCtx;
    }

    public void dispatch(Operation op) throws CanceledException {
        while (true) {
            if (transferCtx.isCancelRequested()) {
                throw new CanceledException();
            }
            try {
                op.send();
                return; // success
            } catch (CanceledException ce) {
                throw ce;
            } catch (FileTransferException fte) {
                OperationError error = OperationError.from(op, fte);
                op = resolveOperationError(error);
            } catch (Throwable t) {
                OperationError error = OperationError.from(op, t);
                op = resolveOperationError(error);
            }
        }
    }

    protected Operation createRecovery(OperationError error) {
        throw createException(error);
    }

    protected TransferException createException(OperationError error) {
        TransferExceptionBuilder srcBuilder = error.getSource().createExceptionBuilder();
        return srcBuilder.setCause(error.getCause()).setTransfer(transferCtx).build();
    }

    private Operation resolveOperationError(OperationError error) {
        if (error.isFatal()) {
            throw createException(error);
        }
        Operation recoveryOp;
        if (error.isCommunication() || error.isBusy()) {
            recoveryOp = error.getSource().createRetryOperation();
        } else {
            recoveryOp = createRecovery(error);
        }
        transferCtx.onTransferRecovery();
        // delay next recovery op
        int delay = transferCtx.getConfig().getRecoveryDelay();
        if (delay > 0) {
            transferCtx.sleep(delay * 1000);
        }
        return recoveryOp;
    }
}