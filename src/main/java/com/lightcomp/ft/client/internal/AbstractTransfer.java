package com.lightcomp.ft.client.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.client.ClientConfig;
import com.lightcomp.ft.client.Transfer;
import com.lightcomp.ft.client.TransferRequest;
import com.lightcomp.ft.client.TransferState;
import com.lightcomp.ft.client.TransferStatus;
import com.lightcomp.ft.client.internal.operations.FinishOperation;
import com.lightcomp.ft.client.internal.operations.RecoveryHandler;
import com.lightcomp.ft.exception.CanceledException;
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.wsdl.v1.FileTransferService;

public abstract class AbstractTransfer implements Runnable, Transfer, RecoveryHandler {

    private static final Logger logger = LoggerFactory.getLogger(AbstractTransfer.class);

    private final TransferStatusImpl status = new TransferStatusImpl();

    protected final TransferRequest request;

    protected final ClientConfig config;

    protected final FileTransferService service;

    private String transferId;

    // flag if cancel was requested
    private volatile boolean cancelRequested;

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

    public boolean isCancelRequested() {
        return cancelRequested;
    }

    @Override
    public synchronized TransferStatus getStatus() {
        return status.copy();
    }

    @Override
    public boolean waitBeforeRecovery(boolean cancelable) {
        TransferStatus ts;
        synchronized (this) {
            status.incrementRecoveryCount();
            // copy status in synch block
            ts = status.copy();
        }
        request.onTransferProgress(ts);

        // wait before recovery
        int delay = config.getRecoveryDelay();
        synchronized (this) {
            if (cancelRequested && cancelable) {
                return false;
            }
            try {
                wait(delay * 1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            return !cancelable || !cancelRequested;
        }
    }

    @Override
    public synchronized void cancel() {
        if (!cancelRequested) {
            // update volatile flag
            cancelRequested = true;
            // wake up transfer thread
            notifyAll();
        }
        TransferState ts = status.getState();
        while (!ts.equals(TransferState.CANCELED)) {
            if (ts == TransferState.FINISHED) {
                throw TransferExceptionBuilder.from(this, "Finished transfer cannot be canceled").build();
            }
            if (ts == TransferState.FAILED) {
                throw TransferExceptionBuilder.from(this, "Failed transfer cannot be canceled").build();
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
            begin();
            transfer();
            finish();
        } catch (Throwable t) {
            transferFailed(t);
        }
    }

    private void begin() throws CanceledException {
        try {
            transferId = service.begin(request.getRequestId());
        } catch (Throwable t) {
            throw TransferExceptionBuilder.from(this, "Failed to begin transfer").setCause(t).build();
        }
        updateState(TransferState.STARTED);
    }

    protected abstract void transfer() throws CanceledException;

    private void finish() throws CanceledException {
        FinishOperation op = new FinishOperation(this, this);
        if (!op.execute(service)) {
            throw new CanceledException();
        }
        synchronized (this) {
            status.changeState(TransferState.FINISHED);
            // notify canceling threads
            notifyAll();
        }
        request.onTransferSuccess();
    }

    protected void updateState(TransferState newState) throws CanceledException {
        TransferStatus ts = null;
        synchronized (this) {
            // update current state
            status.changeState(newState);
            // not finished transfers can be canceled
            if (cancelRequested) {
                throw new CanceledException();
            }
            // copy status in synch block
            ts = status.copy();
            // notify canceling threads
            notifyAll();
        }
        request.onTransferProgress(ts);
    }

    protected void updateProgress(long transferedSize) {
        TransferStatus ts;
        synchronized (this) {
            status.addTransferedSize(transferedSize);
            // copy status in synch block
            ts = status.copy();
        }
        request.onTransferProgress(ts);
    }

    private void transferFailed(Throwable cause) {
        boolean canceled = false;

        synchronized (this) {
            // canceled when CanceledException is thrown and cancel is pending
            canceled = cancelRequested && cause instanceof CanceledException;
            if (canceled) {
                status.changeState(TransferState.CANCELED);
            } else {
                status.changeState(TransferState.FAILED);
            }
            // notify canceling threads
            notifyAll();
        }

        abortServerTransfer();

        if (canceled) {
            request.onTransferCanceled();
        } else {
            TransferException te = TransferExceptionBuilder.from(this, "Transfer failed").setCause(cause).build();
            logger.error(te.getMessage(), te.getCause());
            request.onTransferFailed(te);
        }
    }

    private void abortServerTransfer() {
        if (transferId != null) {
            try {
                service.abort(transferId);
            } catch (Throwable t) {
                String msg = TransferExceptionBuilder.from(this, "Unable to abort server transfer").setCause(t).buildMessage();
                logger.error(msg, t);
            }
        }
    }
}
