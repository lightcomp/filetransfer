package com.lightcomp.ft.client.internal;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.client.ClientConfig;
import com.lightcomp.ft.client.Transfer;
import com.lightcomp.ft.client.TransferRequest;
import com.lightcomp.ft.client.TransferState;
import com.lightcomp.ft.client.TransferStatus;
import com.lightcomp.ft.client.operations.FinishOperation;
import com.lightcomp.ft.client.operations.RecoveryHandler;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.wsdl.v1.FileTransferService;

public abstract class AbstractTransfer implements Runnable, Transfer, RecoveryHandler {

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
        String id = request.getLogId();
        if (id != null) {
            return id;
        }
        return request.getData().getId();
    }

    @Override
    public synchronized TransferStatus getStatus() {
        return status.copy();
    }

    @Override
    public boolean prepareRecovery(boolean interruptible) {
        // update state and report to acceptor
        TransferStatus ts = null;
        synchronized (this) {
            // update current state
            status.incrementRecoveryCount();
            // copy status in synch block
            ts = status.copy();
        }
        request.onTransferProgress(ts);
        // delay recovery operation
        int delay = config.getRecoveryDelay();
        synchronized (this) {
            // transfer could be canceled
            if (cancelRequested && interruptible) {
                return false;
            }
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
        return true;
    }

    @Override
    public void onRecoverySuccess() {
        TransferStatus ts = null;
        synchronized (this) {
            // update current state
            status.resetRecoveryCount();
            // copy status in synch block
            ts = status.copy();
        }
        request.onTransferProgress(ts);
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
            request.onTransferInitialized(this);
            // execute all phases
            if (!begin()) {
                transferCanceled();
                return;
            }
            if (!transfer()) {
                transferCanceled();
                return;
            }
            if (!finish()) {
                transferCanceled();
                return;
            }
        } catch (Throwable t) {
            transferFailed(t);
        }
    }

    protected abstract boolean transferFrames();

    private boolean begin() {
        if (cancelRequested) {
            return false;
        }
        try {
            String transferId = service.begin(request.getData());
            this.transferId = Validate.notBlank(transferId, "Invalid transfer id");
        } catch (Throwable t) {
            throw TransferExceptionBuilder.from("Failed to begin transfer", this).setCause(t).build();
        }
        // notify about progress
        TransferStatus ts;
        synchronized (this) {
            // update current state
            status.changeState(TransferState.STARTED);
            // copy status in synch block
            ts = status.copy();
        }
        request.onTransferProgress(ts);
        return true;
    }

    private boolean transfer() {
        if (cancelRequested) {
            return false;
        }
        // impl transfers all frames
        if (!transferFrames()) {
            return false;
        }
        // notify about progress
        TransferStatus ts;
        synchronized (this) {
            // update current state
            status.changeState(TransferState.TRANSFERED);
            // copy status in synch block
            ts = status.copy();
        }
        request.onTransferProgress(ts);
        return true;
    }

    private boolean finish() {
        if (cancelRequested) {
            return false;
        }
        FinishOperation op = new FinishOperation(this, this);
        if (!op.execute(service)) {
            return false;
        }
        // notify about progress
        synchronized (this) {
            // update current state
            status.changeState(TransferState.FINISHED);
            // notify canceling threads
            notifyAll();
        }
        request.onTransferSuccess(op.getResponse());
        return true;
    }

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
