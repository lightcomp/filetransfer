package com.lightcomp.ft.client.internal;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.client.ClientConfig;
import com.lightcomp.ft.client.Transfer;
import com.lightcomp.ft.client.TransferRequest;
import com.lightcomp.ft.client.TransferState;
import com.lightcomp.ft.client.TransferStatus;
import com.lightcomp.ft.client.operations.FinishOperation;
import com.lightcomp.ft.client.operations.OperationHandler;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.wsdl.v1.FileTransferService;
import com.lightcomp.ft.xsd.v1.GenericData;

public abstract class AbstractTransfer implements Runnable, Transfer, OperationHandler {

    private static final Logger logger = LoggerFactory.getLogger(AbstractTransfer.class);

    protected final TransferStatusImpl status = new TransferStatusImpl();

    protected final TransferRequest request;

    protected final ClientConfig config;

    protected final FileTransferService service;

    protected String transferId;

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
        GenericData data = request.getData();
        if (data != null) {
            return data.getId();
        }
        return null;
    }

    public synchronized boolean isCancelRequested() {
        return cancelRequested;
    }

    @Override
    public synchronized TransferStatus getStatus() {
        return status.copy();
    }

    @Override
    public synchronized void recoverySucceeded() {
        status.resetRecoveryCount();
    }

    @Override
    public boolean prepareRecovery(boolean interruptible) {
        // increment recovery count
        TransferStatus ts = null;
        synchronized (this) {
            // update current state
            status.incrementRecoveryCount();
            // copy status in synch block
            ts = status.copy();
        }
        request.onTransferProgress(ts);
        // delay transfer before recovery
        int delay = config.getRecoveryDelay();
        synchronized (this) {
            // check if canceled
            if (cancelRequested && interruptible) {
                return false;
            }
            if (delay > 0) {
                try {
                    wait(delay * 1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                // check if thread awakened by cancel
                if (cancelRequested && interruptible) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public synchronized void cancel() {
        if (!cancelRequested) {
            // we can set only flag
            cancelRequested = true;
            // wake up transfer thread
            notifyAll();
        }
        // wait for termination
        while (true) {
            switch (status.getState()) {
                case FINISHED:
                    throw TransferExceptionBuilder.from("Finished transfer cannot be canceled", this).build();
                case FAILED:
                    throw TransferExceptionBuilder.from("Failed transfer cannot be canceled", this).build();
                case CANCELED:
                    return;
                default:
                    try {
                        wait(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
            }
        }
    }

    @Override
    public void run() {
        try {
            request.onTransferInitialized(this);
            // execute transfer phases
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
        if (isCancelRequested()) {
            return false;
        }
        try {
            transferId = service.begin(request.getData());
        } catch (FileTransferException e) {
            throw TransferExceptionBuilder.from("Failed to begin transfer").setCause(e).build();
        }
        // validate returned id
        if (StringUtils.isEmpty(transferId)) {
            throw TransferExceptionBuilder.from("Server returned empty transfer id").build();
        }
        // change state to started
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
        if (isCancelRequested()) {
            return false;
        }
        if (!transferFrames()) {
            return false;
        }
        // change state to transfered
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
        if (isCancelRequested()) {
            return false;
        }
        FinishOperation op = new FinishOperation(transferId, this);
        if (!op.execute(service)) {
            return false;
        }
        // change state to finished
        synchronized (this) {
            // update current state
            status.changeState(TransferState.FINISHED);
            // notify canceling threads
            notifyAll();
        }
        request.onTransferSuccess(op.getResponse());
        return true;
    }

    public void transferCanceled() {
        synchronized (this) {
            // update current state
            status.changeState(TransferState.CANCELED);
            // notify canceling threads
            notifyAll();
        }
        // try to abort server transfer
        abortServerTransfer();
        // report canceled transfer
        request.onTransferCanceled();
    }

    public void transferFailed(Throwable cause) {
        synchronized (this) {
            // update current state
            status.changeState(TransferState.FAILED);
            // notify canceling threads
            notifyAll();
        }
        // try to abort server transfer when not fatal
        ExceptionType type = ExceptionType.resolve(cause);
        if (type != ExceptionType.FATAL) {
            abortServerTransfer();
        }
        // log and report failed transfer
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
