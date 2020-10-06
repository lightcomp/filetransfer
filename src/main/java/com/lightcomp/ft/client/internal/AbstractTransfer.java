package com.lightcomp.ft.client.internal;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.client.ClientConfig;
import com.lightcomp.ft.client.Transfer;
import com.lightcomp.ft.client.TransferRequest;
import com.lightcomp.ft.client.TransferState;
import com.lightcomp.ft.client.TransferStatus;
import com.lightcomp.ft.client.internal.operations.BeginOperation;
import com.lightcomp.ft.client.internal.operations.BeginResult;
import com.lightcomp.ft.client.internal.operations.FinishOperation;
import com.lightcomp.ft.client.internal.operations.FinishResult;
import com.lightcomp.ft.client.internal.operations.OperationError;
import com.lightcomp.ft.client.internal.operations.OperationHandler;
import com.lightcomp.ft.client.internal.operations.OperationResult;
import com.lightcomp.ft.client.internal.operations.OperationResult.Type;
import com.lightcomp.ft.exception.TransferExBuilder;
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.wsdl.v1.FileTransferService;
import com.lightcomp.ft.xsd.v1.AbortRequest;
import com.lightcomp.ft.xsd.v1.GenericDataType;

public abstract class AbstractTransfer implements Runnable, Transfer, OperationHandler {

    private static final Logger logger = LoggerFactory.getLogger(AbstractTransfer.class);

    protected final TransferStatusImpl status = new TransferStatusImpl();

    private final TransferRequest request;

    protected final ClientConfig config;

    protected final FileTransferService service;

    protected String transferId;

    private boolean cancelRequested;

    private Thread runningThread;

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
        GenericDataType data = request.getData();
        if (data != null) {
            return data.getId();
        }
        return null;
    }

    @Override
    public synchronized TransferStatus getStatus() {
        return status.copy();
    }

    @Override
    public boolean prepareRecovery() {
        // increment recovery count
        TransferStatus ts = null;
        synchronized (this) {
            status.incrementRetryCount();
            // copy status in synch block
            ts = status.copy();
        }
        onTransferProgress(ts);
        // delay transfer before recovery
        return delayBeforeRecovery();
    }

    private boolean delayBeforeRecovery() {
        int delay = config.getRecoveryDelay();
        synchronized (this) {
            // operation is cancelable if not finishing
            boolean cancelable = status.getState() != TransferState.TRANSFERED;
            // do not wait if canceled
            if (cancelRequested && cancelable) {
                return false;
            }
            if (delay > 0) {
                logger.info("Transfer waiting {}s before recovery ...", delay);
                try {
                    wait(delay * 1000);
                } catch (InterruptedException e) {
                    // ignore
                }
                // check if thread awakened by cancel
                if (cancelRequested && cancelable) {
                    return false;
                }
            }
            return true;
        }
    }

    @Override
    public synchronized void cancel() throws TransferException {
        if (!cancelRequested) {
            cancelRequested = true;
            // wake up transfer thread
            notifyAll();
        }
        while (true) {
            switch (status.getState()) {
                case FINISHED:
                    throw new TransferExBuilder("Finished transfer cannot be canceled", this).build();
                case FAILED:
                    throw new TransferExBuilder("Failed transfer cannot be canceled", this).build();
                case CANCELED:
                    return;
                default:
                    // execute immediately if called by callback
                    if (runningThread == Thread.currentThread()) {
                        Validate.isTrue(cancelIfRequested());
                        return;
                    }
                    // wait until terminated
                    try {
                        wait(100);
                    } catch (InterruptedException e) {
                        // ignore
                    }
            }
        }
    }

    @Override
    public void run() {
        runningThread = Thread.currentThread();
        try {
            request.onTransferInitialized(this);
            // execute transfer phases
            if (!begin()) {
                return;
            }
            if (!transfer()) {
                return;
            }
            if (!finish()) {
                return;
            }
        } catch (Throwable t) {
            TransferExBuilder teb = new TransferExBuilder("Transfer failed", this).setCause(t);
            teb.log(logger);
            ExceptionType type = ExceptionType.resolve(t);
            transferFailed(type);
        }
    }

    private boolean begin() {
        if (cancelIfRequested()) {
            return false;
        }
        // send begin to server
        BeginOperation op = new BeginOperation(service, request.getData());
        BeginResult result = op.execute();
        if (result.getType() != Type.SUCCESS) {
            operationFailed(result);
            return false;
        }
        // set received transfer id
        transferId = result.getTransferId();
        // change state to started
        TransferStatus ts;
        synchronized (this) {
            status.changeState(TransferState.STARTED);
            // always reset retry count
            status.resetRetryCount();
            // copy status in synch block
            ts = status.copy();
        }
        onTransferProgress(ts);
        return true;
    }

    private boolean transfer() throws TransferException {
        if (cancelIfRequested()) {
            return false;
        }
        if (!transferFrames()) {
            return false;
        }
        // change state to transfered;
        TransferStatus ts;
        synchronized (this) {
            status.changeState(TransferState.TRANSFERED);
            // always reset retry count
            status.resetRetryCount();
            // copy status in synch block
            ts = status.copy();
        }
        onTransferProgress(ts);
        return true;
    }

    protected abstract boolean transferFrames() throws TransferException;

    /**
     * Reports progress to request. Any runtime exception from callback is propagated to caller. Method
     * must not be synchronized.
     */
    protected void onTransferProgress(TransferStatus status) {
        request.onTransferProgress(status);
    }

    protected void frameProcessed(int seqNum) {
        TransferStatus ts;
        synchronized (this) {
            Validate.isTrue(status.getLastFrameSeqNum() + 1 == seqNum);
            status.incrementFrameSeqNum();
            // always reset retry count
            status.resetRetryCount();
            // copy status in synch block
            ts = status.copy();
        }
        onTransferProgress(ts);
    }

    private boolean finish() {
        if (cancelIfRequested()) {
            return false;
        }
        // send finish to server
        FinishOperation op = new FinishOperation(this, service);
        FinishResult result = op.execute();
        if (result.getType() != Type.SUCCESS) {
            operationFailed(result);
            return false;
        }
        // change state to finished
        synchronized (this) {
            status.changeState(TransferState.FINISHED);
            // always reset retry count
            status.resetRetryCount();
            // notify canceling threads
            notifyAll();
        }
        request.onTransferSuccess(result.getData());
        return true;
    }

    protected boolean cancelIfRequested() {
        synchronized (this) {
            if (!cancelRequested) {
                return false;
            }
            status.changeState(TransferState.CANCELED);
            // notify canceling threads
            notifyAll();
        }
        logger.info("Transfer canceled, transferId={}", transferId);
        // try to abort server transfer
        abortServerTransfer();
        // report cancel to request
        try {
            request.onTransferCanceled();
        } catch (Throwable t) {
            TransferExBuilder teb = new TransferExBuilder("Cancel callback of request cause exception", this)
                    .setCause(t);
            teb.log(logger);
        }
        return true;
    }

    protected void operationFailed(OperationResult result) {
        OperationResult.Type type = result.getType();
        Validate.isTrue(type != Type.SUCCESS);
        // try cancel first
        if (type == Type.CANCEL && cancelIfRequested()) {
            return;
        }
        // log operation detail
        OperationError err = result.getError();
        TransferExBuilder teb = new TransferExBuilder(err.getMessage(), this).addParams(err.getParams())
                .setCause(err.getCause());
        teb.log(logger);
        // fail transfer
        transferFailed(err.getCauseType());
    }

    private void transferFailed(ExceptionType type) {
        synchronized (this) {
            status.changeState(TransferState.FAILED);
            // notify canceling threads
            notifyAll();
        }
        // try to abort server transfer when not fatal
        if (type != ExceptionType.FATAL) {
            abortServerTransfer();
        }
        // report fail to request
        try {
            request.onTransferFailed();
        } catch (Throwable t) {
            TransferExBuilder teb = new TransferExBuilder("Fail callback of request cause exception", this).setCause(t);
            teb.log(logger);
        }
    }

    private void abortServerTransfer() {
        if (transferId == null) {
            return;
        }
        AbortRequest ar = new AbortRequest();
        ar.setTransferId(transferId);
        try {
            service.abort(ar);
        } catch (Throwable t) {
            TransferExBuilder teb = new TransferExBuilder("Unable to abort server transfer", this).setCause(t);
            teb.log(logger);
        }
    }
}
