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
import com.lightcomp.ft.client.internal.operations.OperationDispatcher;
import com.lightcomp.ft.exception.CanceledException;
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.exception.TransferExceptionBuilder;

import cxf.FileTransferService;

public abstract class AbstractTransfer implements Runnable, Transfer, TransferContext {

    protected static final Logger logger = LoggerFactory.getLogger(AbstractTransfer.class);

    protected final OperationDispatcher opDispatcher = new OperationDispatcher(this);

    protected final TransferRequest request;

    protected final ClientConfig config;

    protected final FileTransferService service;

    private final TransferStatusImpl status = new TransferStatusImpl();

    // flag if cancel was requested
    protected volatile boolean cancelRequested;

    protected String transferId;

    public AbstractTransfer(TransferRequest request, ClientConfig config, FileTransferService service) {
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
    public ClientConfig getConfig() {
        return config;
    }

    @Override
    public FileTransferService getService() {
        return service;
    }

    @Override
    public boolean isCancelRequested() {
        return cancelRequested;
    }

    @Override
    public synchronized TransferStatus getStatus() {
        return status.copy();
    }

    public void onDataSent(long size) {
        TransferStatus ts;
        synchronized (this) {
            status.addTransferedSize(size);
            // copy status in synch block
            ts = status.copy();
        }
        request.onTransferProgress(ts);
    }

    @Override
    public void onTransferRecovery() {
        TransferStatus ts;
        synchronized (this) {
            status.incrementRecoveryCount();
            // copy status in synch block
            ts = status.copy();
        }
        request.onTransferProgress(ts);
    }

    @Override
    public synchronized void sleep(long ms) {
        try {
            wait(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
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
                throw TransferExceptionBuilder.from("Finished transfer cannot be canceled").setTransfer(this).build();
            }
            if (ts == TransferState.FAILED) {
                throw TransferExceptionBuilder.from("Failed transfer cannot be canceled").setTransfer(this).build();
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
            handleTransferFailure(t);
        }
    }

    protected abstract void transfer() throws CanceledException;

    protected void updateState(TransferState newState) throws CanceledException {
        TransferStatus ts = null;
        synchronized (this) {
            // update current state
            status.changeState(newState);
            // not committed transfers can be canceled
            if (newState != TransferState.FINISHED) {
                if (cancelRequested) {
                    throw new CanceledException();
                }
                // copy status in synch block
                ts = status.copy();
            }
            // notify canceling threads
            notifyAll();
        }
        if (newState == TransferState.FINISHED) {
            request.onTransferSuccess();
        } else {
            request.onTransferProgress(ts);
        }
    }

    private void begin() throws CanceledException {
        Validate.isTrue(transferId == null);
        try {
            transferId = service.begin(request.getRequestId());
        } catch (Throwable t) {
            throw TransferExceptionBuilder.from("Failed to begin transfer").setTransfer(this).setCause(t).build();
        }
        updateState(TransferState.STARTED);
    }

    private void finish() throws CanceledException {
        FinishOperation op = new FinishOperation(service, transferId);
        opDispatcher.dispatch(op);
        updateState(TransferState.FINISHED);
    }

    private void handleTransferFailure(Throwable cause) {
        boolean canceled;
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
        abortServer();

        if (canceled) {
            request.onTransferCanceled();
        } else {
            TransferException te = TransferExceptionBuilder.from("Transfer failed").setTransfer(this).setCause(cause).build();
            logger.error(te.getMessage(), te.getCause());
            request.onTransferFailed(te);
        }
    }

    private void abortServer() {
        try {
            service.abort(transferId);
        } catch (Throwable t) {
            String msg = TransferExceptionBuilder.from("Unable to abort server").setTransfer(this).buildMessage();
            logger.error(msg, t);
        }
    }
}
