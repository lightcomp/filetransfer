package com.lightcomp.ft.client.internal;

import org.apache.commons.lang3.Validate;
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

    protected final TransferStatusImpl status = new TransferStatusImpl();

    protected final TransferRequest request;

    protected final ClientConfig config;

    protected final FileTransferService service;

    private String transferId;

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

    @Override
    public synchronized TransferStatus getStatus() {
        return status.copy();
    }

    public void checkCancelRequest() throws CanceledException {
        if (cancelRequested) {
            throw new CanceledException();
        }
    }

    public void onLastFrameTransfered() {
        TransferStatus ts;
        synchronized (this) {
            // check transfer state
            Validate.isTrue(status.getState() == TransferState.STARTED);
            // update current state
            status.changeState(TransferState.TRANSFERED);
            // copy status in synch block
            ts = status.copy();
        }
        request.onTransferProgress(ts);
    }

    @Override
    public boolean waitBeforeRecovery(boolean cancelable) {
        TransferStatus ts;
        synchronized (this) {
            if (cancelable && cancelRequested) {
                return false;
            }
            // check transfer state
            Validate.isTrue(status.getState().ordinal() < TransferState.FINISHED.ordinal());
            // update status
            status.incrementRecoveryCount();
            // copy in synch block
            ts = status.copy();
        }
        request.onTransferProgress(ts);
    }

    @Override
    public synchronized void waitBeforeRecovery() {
        int secDelay = config.getRecoveryDelay();
        try {
            wait(secDelay * 1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
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

    protected abstract void transfer() throws CanceledException;

    private void begin() throws CanceledException {
        checkCancelRequest();
        // try to send begin message
        try {
            transferId = service.begin(request.getRequestId());
        } catch (Throwable t) {
            throw TransferExceptionBuilder.from(this, "Failed to begin transfer").setCause(t).build();
        }
        // update state if succeed
        TransferStatus ts = null;
        synchronized (this) {
            checkCancelRequest();
            // update current state
            status.changeState(TransferState.STARTED);
            // copy status in synch block
            ts = status.copy();
        }
        request.onTransferProgress(ts);
    }

    private void finish() throws CanceledException {
        checkCancelRequest();
        // try to send finish message
        FinishOperation op = new FinishOperation(this, this);
        op.execute(service);
        // update state if succeed
        synchronized (this) {
            checkCancelRequest();
            // update current state
            status.changeState(TransferState.FINISHED);
            // notify canceling threads
            notifyAll();
        }
        request.onTransferSuccess();
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
