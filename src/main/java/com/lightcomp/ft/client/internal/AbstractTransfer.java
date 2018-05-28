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
import com.lightcomp.ft.client.internal.operations.FinishOperation;
import com.lightcomp.ft.client.internal.operations.OperationHandler;
import com.lightcomp.ft.client.internal.operations.OperationStatus;
import com.lightcomp.ft.client.internal.operations.OperationStatus.Type;
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.wsdl.v1.FileTransferService;
import com.lightcomp.ft.xsd.v1.AbortRequest;
import com.lightcomp.ft.xsd.v1.GenericDataType;

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
        request.onTransferProgress(ts);
        // delay transfer before recovery
        return delayBeforeRecovery();
    }

    @Override
    public synchronized void recoverySucceeded() {
        status.resetRetryCount();
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
            // we can set only flag
            cancelRequested = true;
            // wake up transfer thread
            notifyAll();
        }
        while (true) {
            switch (status.getState()) {
                case FINISHED:
                    throw new TransferExceptionBuilder("Finished transfer cannot be canceled", this).build();
                case FAILED:
                    throw new TransferExceptionBuilder("Failed transfer cannot be canceled", this).build();
                case CANCELED:
                    return;
                default:
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
            transferFailed(t);
        }
    }

    protected abstract boolean transferFrames() throws TransferException;

    protected void frameProcessed(int seqNum) {
        TransferStatus ts;
        synchronized (this) {
            Validate.isTrue(status.getLastFrameSeqNum() + 1 == seqNum);
            status.incrementFrameSeqNum();
            // copy status in synch block
            ts = status.copy();
        }
        request.onTransferProgress(ts);
    }

    private boolean begin() {
        if (cancelIfRequested()) {
            return false;
        }
        BeginOperation bo = new BeginOperation(this, service, request.getData());
        OperationStatus bos = bo.execute();
        if (bos.getType() != Type.SUCCESS) {
            transferFailed(bos);
            return false;
        }
        transferId = bo.getTransferId();
        // change state to started
        TransferStatus ts;
        synchronized (this) {
            status.changeState(TransferState.STARTED);
            // copy status in synch block
            ts = status.copy();
        }
        request.onTransferProgress(ts);
        return true;
    }

    private boolean transfer() throws TransferException {
        if (cancelIfRequested()) {
            return false;
        }
        if (!transferFrames()) {
            return false;
        }
        // change state to transfered
        TransferStatus ts;
        synchronized (this) {
            status.changeState(TransferState.TRANSFERED);
            // copy status in synch block
            ts = status.copy();
        }
        request.onTransferProgress(ts);
        return true;
    }

    private boolean finish() {
        if (cancelIfRequested()) {
            return false;
        }
        FinishOperation fo = new FinishOperation(this, service);
        OperationStatus fos = fo.execute();
        if (fos.getType() != Type.SUCCESS) {
            transferFailed(fos);
            return false;
        }
        // change state to finished
        synchronized (this) {
            status.changeState(TransferState.FINISHED);
            // notify canceling threads
            notifyAll();
        }
        request.onTransferSuccess(fo.getResponse());
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
        // try to abort server transfer
        abortServerTransfer();
        // report canceled transfer
        request.onTransferCanceled();
        return true;
    }

    protected void transferFailed(OperationStatus os) {
        Type type = os.getType();
        Validate.isTrue(type != Type.SUCCESS);
        // try cancel transfer
        if (type == Type.CANCEL && cancelIfRequested()) {
            return;
        }
        // log message detail
        new TransferExceptionBuilder(os.getFailureMessage(), this).addParams(os.getFailureParams())
                .setCause(os.getFailureCause()).log(logger);
        // fail transfer
        transferFailed(os.getFailureCause(), os.getFailureType());
    }

    protected void transferFailed(Throwable cause) {
        new TransferExceptionBuilder("Transfer failed", this).setCause(cause).log(logger);
        ExceptionType type = ExceptionType.resolve(cause);
        transferFailed(cause, type);
    }

    private void transferFailed(Throwable cause, ExceptionType type) {
        synchronized (this) {
            status.changeState(TransferState.FAILED);
            // notify canceling threads
            notifyAll();
        }
        // try to abort server transfer when not fatal
        if (type != ExceptionType.FATAL) {
            abortServerTransfer();
        }
        request.onTransferFailed();
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
            new TransferExceptionBuilder("Unable to abort server transfer", this).setCause(t).log(logger);
        }
    }
}
