package com.lightcomp.ft.client.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.client.ClientConfig;
import com.lightcomp.ft.client.Transfer;
import com.lightcomp.ft.client.TransferRequest;
import com.lightcomp.ft.client.TransferState;
import com.lightcomp.ft.client.TransferStatus;
import com.lightcomp.ft.client.operations.BeginOperation;
import com.lightcomp.ft.client.operations.FinishOperation;
import com.lightcomp.ft.client.operations.Operation;
import com.lightcomp.ft.client.operations.OperationListener;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.wsdl.v1.FileTransferService;

public abstract class AbstractTransfer implements Runnable, Transfer, OperationListener {

    private static final Logger logger = LoggerFactory.getLogger(AbstractTransfer.class);

    protected final TransferStatusImpl status = new TransferStatusImpl();

    protected final TransferRequest request;

    protected final ClientConfig config;

    protected final FileTransferService service;

    private String transferId;

    private boolean cancelRequested;

    protected AbstractTransfer(TransferRequest request, ClientConfig config, FileTransferService service) {
        this.request = request;
        this.config = config;
        this.service = service;
    }

    @Override
    public String getTransferId() {
        return transferId;
    }

    @Override
    public String getRequestId() {
        return request.getRequestId();
    }

    @Override
    public synchronized TransferStatus getStatus() {
        return status.copy();
    }

    @Override
    public boolean isOperationFeasible(Operation operation) {
        boolean interruptible = operation.isInterruptible();
        int recoveryCount = operation.getRecoveryCount();
        // check if not canceled and update recovery count
        TransferStatus ts = null;
        synchronized (this) {
            // cancel if requested and operation is interruptible
            if (cancelRequested && interruptible) {
                return false;
            }
            if (status.getRecoveryCount() != recoveryCount) {
                // update current state
                status.setRecoveryCount(recoveryCount);
                // copy status in synch block
                ts = status.copy();
            }
        }
        // update progress if count changed
        if (ts != null) {
            request.onTransferProgress(ts);
        }
        // delay recovery operation
        if (operation.getRecoveryCount() > 0) {
            int delay = config.getRecoveryDelay();
            synchronized (this) {
                try {
                    wait(delay * 1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                // transfer could be awakened by cancel
                if (cancelRequested && interruptible) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void onBeginSuccess(String transferId) {
        TransferStatus ts;
        synchronized (this) {
            // update current state
            status.changeState(TransferState.STARTED);
            // copy status in synch block
            ts = status.copy();
        }
        request.onTransferProgress(ts);
    }

    @Override
    public void onLastFrameSuccess() {
        TransferStatus ts;
        synchronized (this) {
            // update current state
            status.changeState(TransferState.TRANSFERED);
            // copy status in synch block
            ts = status.copy();
        }
        request.onTransferProgress(ts);
    }

    @Override
    public void onFinishSuccess() {
        synchronized (this) {
            // update current state
            status.changeState(TransferState.FINISHED);
            // notify canceling threads
            notifyAll();
        }
        request.onTransferSuccess();
    }

    @Override
    public synchronized void cancel() {
        // update volatile flag
        cancelRequested = true;
        // wake up transfer thread
        notifyAll();
        // wait for termination
        TransferState ts = status.getState();
        while (!ts.equals(TransferState.CANCELED)) {
            if (ts == TransferState.FINISHED) {
                throw TransferExceptionBuilder.from("Finished transfer cannot be canceled", this).build();
            }
            if (ts == TransferState.FAILED) {
                throw TransferExceptionBuilder.from("Failed transfer cannot be canceled", this).build();
            }
            try {
                wait(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            ts = status.getState();
        }
    }

    @Override
    public void run() {
        try {
            BeginOperation bop = new BeginOperation(this, transferId);
            if (!bop.execute(service)) {
                transferCanceled();
                return;
            }
            if (!transferData()) {
                transferCanceled();
                return;
            }
            FinishOperation fop = new FinishOperation(this, this);
            if (!fop.execute(service)) {
                transferCanceled();
                return;
            }
        } catch (Throwable t) {
            transferFailed(t);
        }
    }

    protected abstract boolean transferData();

    private void transferCanceled() {
        synchronized (this) {
            // update current state
            status.changeState(TransferState.CANCELED);
            // notify canceling threads
            notifyAll();
        }
        // try to abort server transfer
        abortServerTransfer();
        // notify listener
        request.onTransferCanceled();
    }

    private void transferFailed(Throwable cause) {
        synchronized (this) {
            // update current state
            status.changeState(TransferState.FAILED);
            // notify canceling threads
            notifyAll();
        }
        // try to abort server transfer
        abortServerTransfer();
        // log error and notify listener
        TransferExceptionBuilder.from("Transfer failed", this).setCause(cause).log(logger);
        request.onTransferFailed(cause);
    }

    private void abortServerTransfer() {
        if (transferId != null) {
            try {
                service.abort(transferId);
            } catch (Throwable t) {
                TransferExceptionBuilder.from("Unable to abort server transfer", this).setCause(t).log(logger);
            }
        }
    }
}
