package com.lightcomp.ft.sender.impl.phase;

import com.lightcomp.ft.TransferInfo;
import com.lightcomp.ft.exception.CanceledException;
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.sender.impl.TransferContext;
import com.lightcomp.ft.sender.impl.phase.operation.Operation;

import cxf.FileTransferException;
import cxf.FileTransferService;

public abstract class RecoverablePhase implements Phase {

    protected final TransferContext transferCtx;

    protected RecoverablePhase(TransferContext transferCtx) {
        this.transferCtx = transferCtx;
    }

    @Override
    public final void process() throws CanceledException {
        Operation op = null;

        while (true) {
            if (transferCtx.isCancelRequested()) {
                throw new CanceledException();
            }
            try {
                if (op == null) {
                    op = getNextOperation();
                    if (op == null) {
                        return; // no more operation
                    }
                }
                op.send();
                op = null; // clear last operation if succeeded
            } catch (CanceledException ce) {
                throw ce;
            } catch (FileTransferException fte) {
                OperationError error = OperationError.from(fte);
                op = resolveOperationError(op, error);
            } catch (Throwable t) {
                OperationError error = OperationError.from(t);
                op = resolveOperationError(op, error);
            }
        }
    }

    public TransferInfo getTransferInfo() {
        return transferCtx;
    }

    public FileTransferService getService() {
        return transferCtx.getService();
    }

    /**
     * @return Returns next operation or null when the phase does not have more.
     */
    protected abstract Operation getNextOperation() throws CanceledException;

    protected abstract TransferException createTransferException(OperationError error);

    protected Operation createRecovery(OperationError error) {
        throw createTransferException(error);
    }

    private Operation resolveOperationError(Operation op, OperationError error) {
        if (error.isFatal()) {
            throw createTransferException(error);
        }
        if (error.isCommunication() || error.isBusy()) {
            op = op.createRetry();
        } else {
            op = createRecovery(error);
        }
        transferCtx.onTransferRecovery();
        // delay transfer before recovery operation
        int delay = transferCtx.getSenderConfig().getRecoveryDelay();
        if (delay > 0) {
            transferCtx.sleep(delay * 1000, true);
        }
        return op;
    }
}