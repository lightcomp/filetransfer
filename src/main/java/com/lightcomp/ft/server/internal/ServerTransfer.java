package com.lightcomp.ft.server.internal;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.common.TaskExecutor;
import com.lightcomp.ft.core.TransferInfo;
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.exception.TransferExBuilder;
import com.lightcomp.ft.server.ServerConfig;
import com.lightcomp.ft.server.TransferDataHandler;
import com.lightcomp.ft.server.TransferState;
import com.lightcomp.ft.server.TransferStatus;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.xsd.v1.ErrorCode;
import com.lightcomp.ft.xsd.v1.Frame;
import com.lightcomp.ft.xsd.v1.GenericDataType;

public abstract class ServerTransfer implements Transfer, TransferInfo {

    private static final Logger logger = LoggerFactory.getLogger(ServerTransfer.class);

    protected final TransferStatusImpl status = new TransferStatusImpl();

    protected final String transferId;

    private final TransferDataHandler handler;

    protected final ServerConfig config;

    protected final TaskExecutor executor;

    protected ServerTransfer(String transferId, TransferDataHandler handler, ServerConfig config,
            TaskExecutor executor) {
        this.transferId = transferId;
        this.handler = handler;
        this.config = config;
        this.executor = executor;
    }

    @Override
    public String getTransferId() {
        return transferId;
    }

    @Override
    public String getRequestId() {
        return handler.getRequestId();
    }

    public synchronized boolean isBusy() {
        TransferState state = status.getState();
        if (state.isTerminal()) {
            return false;
        }
        if (state == TransferState.FINISHING) {
            return true;
        }
        return isBusyInternal();
    }

    protected abstract boolean isBusyInternal();

    /**
     * Initializes transfer, after that is transfer able to send/receive. In case of fail the server
     * never publishes an uninitialized transfer.
     */
    public void init() throws TransferException {
        TransferStatus ts;
        synchronized (this) {
            status.changeState(TransferState.STARTED);
            // copy status in synch block
            ts = status.copy();
        }
        // exception is caught by server creation process
        onTransferProgress(ts);
    }

    public synchronized TransferStatus getStatus() {
        return status.copy();
    }

    @Override
    public synchronized TransferStatus getConfirmedStatus() throws FileTransferException {
        if (isBusy()) {
            throw new ServerError("Transfer is busy", this).setCode(ErrorCode.BUSY).createEx();
        }
        return status.copy();
    }

    @Override
    public void recvFrame(Frame frame) throws FileTransferException {
        ServerError err = new ServerError("Transfer cannot receive frame in download mode", this);
        transferFailed(err);
        throw err.createEx();
    }

    @Override
    public Frame sendFrame(int seqNum) throws FileTransferException {
        ServerError err = new ServerError("Transfer cannot send frame in upload mode", this);
        transferFailed(err);
        throw err.createEx();
    }

    @Override
    public GenericDataType finish() throws FileTransferException {
        ServerError err = null;
        synchronized (this) {
            checkActiveTransfer();
            // check transfered state
            if (status.getState() != TransferState.TRANSFERED) {
                err = new ServerError("Not all frames were transfered", this);
                status.changeStateToFailed(err.getDesc());
                // notify canceling threads
                notifyAll();
            } else {
                status.changeState(TransferState.FINISHING);
            }
        }
        // handle any error outside of sync block
        if (err != null) {
            if (err.isFatal()) {
                onTransferFailed(err);
            }
            throw err.createEx();
        }
        return finishInternal();
    }

    private GenericDataType finishInternal() throws FileTransferException {
        GenericDataType resp;
        try {
            resp = handler.finishTransfer();
        } catch (Throwable t) {
            ServerError err = new ServerError("Success callback of data handler cause exception", this).setCause(t);
            transferFailed(err);
            throw err.createEx();
        }
        synchronized (this) {
            Validate.isTrue(status.getState() == TransferState.FINISHING);
            status.changeStateToFinished(resp);
            // notify canceling threads
            notifyAll();
        }
        return resp;
    }

    @Override
    public void abort() throws FileTransferException {
        synchronized (this) {
            // check terminal states
            TransferState state = status.getState();
            if (state == TransferState.FINISHING) {
                throw new ServerError("Finishing transfer cannot be aborted", this).createEx();
            }
            if (state == TransferState.FINISHED) {
                throw new ServerError("Finished transfer cannot be aborted", this).createEx();
            }
            if (state.isTerminal()) {
                return; // aborted, canceled and failed are OK
            }
            status.changeState(TransferState.ABORTED);
            // notify other canceling threads
            notifyAll();
        }
        onTransferCanceled();
    }

    public void cancel() throws TransferException {
        synchronized (this) {
            // check terminal states
            TransferState state = status.getState();
            if (state == TransferState.FINISHING) {
                throw new TransferExBuilder("Finishing transfer cannot be canceled", this).build();
            }
            if (state == TransferState.FINISHED) {
                throw new TransferExBuilder("Finished transfer cannot be canceled", this).build();
            }
            if (state == TransferState.FAILED) {
                throw new TransferExBuilder("Failed transfer cannot be canceled", this).build();
            }
            if (state.isTerminal()) {
                return; // aborted and canceled are OK
            }
            status.changeState(TransferState.CANCELED);
            // notify other canceling threads
            notifyAll();
        }
        onTransferCanceled();
    }

    public void terminate() {
        boolean canceled = false;
        synchronized (this) {
            waitWhileFinishing();
            // if transfer is terminated we are done
            if (!status.getState().isTerminal()) {
                status.changeState(TransferState.CANCELED);
                canceled = true;
                // notify canceling threads
                notifyAll();
            }
        }
        if (canceled) {
            onTransferCanceled();
        }
        cleanResources();
    }

    public boolean terminateIfInactive() {
        ServerError err = null;
        synchronized (this) {
            waitWhileFinishing();
            // if transfer is terminated we are done
            if (!status.getState().isTerminal()) {
                // check if transfer is active
                long timeout = config.getInactiveTimeout();
                LocalDateTime timeoutLimit = LocalDateTime.now().minus(timeout, ChronoUnit.SECONDS);
                LocalDateTime lastActivity = status.getLastActivity();
                if (timeoutLimit.isBefore(lastActivity)) {
                    return false;
                }
                // inactive transfer
                err = new ServerError("Inactivity timeout reached", this);
                status.changeStateToFailed(err.getDesc());
                // notify canceling threads
                notifyAll();
            }
        }
        if (err != null) {
            onTransferFailed(err);
        }
        cleanResources();
        return true;
    }

    /**
     * Cleans up all transfer resources. Method is called before server terminates transfer.
     */
    protected abstract void cleanResources();

    /**
     * Method waits while transfer is finishing. Caller must ensure synchronization.
     */
    private void waitWhileFinishing() {
        while (status.getState() == TransferState.FINISHING) {
            try {
                wait(100);
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    /**
     * Fails transfer and notifies data handler. When transfer is already terminated error is logged.
     * Method must not be synchronized.
     */
    protected void transferFailed(ServerError err) {
        boolean failed = false;
        synchronized (this) {
            // check if this error is source of failure
            if (status.getState() == TransferState.FAILED) {
                failed = err.getDesc() == status.getErrorDesc();
            }
            // change to fail if not already terminated
            else if (!status.getState().isTerminal()) {
                status.changeStateToFailed(err.getDesc());
                failed = true;
                // notify canceling threads
                notifyAll();
            }
        }
        if (!failed) {
            err.log(logger, "Terminated trasfer thrown exception");
            return;
        }
        onTransferFailed(err);
    }

    /**
     * Reports progress to data handler. Any runtime exception from callback is propagated to caller.
     * Method must not be synchronized.
     */
    protected void onTransferProgress(TransferStatus status) {
        handler.onTransferProgress(status);
    }

    /**
     * Reports transfer fail to data handler. Any runtime exception from callback is only logged. Method
     * must not be synchronized.
     */
    protected void onTransferFailed(ServerError err) {
        err.log(logger);
        try {
            handler.onTransferFailed(err.getDesc());
        } catch (Throwable t) {
            err = new ServerError("Fail callback of data handler cause exception", this).setCause(t);
            err.log(logger);
        }
    }

    /**
     * Reports transfer cancel to data handler. Any runtime exception from callback is only logged.
     * Method must not be synchronized.
     */
    private void onTransferCanceled() {
        logger.info("Transfer canceled, transferId={}", transferId);
        try {
            handler.onTransferCanceled();
        } catch (Throwable t) {
            ServerError err = new ServerError("Cancel callback of data handler cause exception", this).setCause(t);
            err.log(logger);
        }
    }

    /**
     * If passed the check transfer cannot be busy and terminated. Caller must ensure synchronization.
     */
    protected void checkActiveTransfer() throws FileTransferException {
        if (isBusy()) {
            throw new ServerError("Transfer is busy", this).setCode(ErrorCode.BUSY).createEx();
        }
        switch (status.getState()) {
            case CREATED:
                throw new IllegalStateException();
            case FINISHED:
                throw new ServerError("Transfer finished", this).createEx();
            case CANCELED:
                throw new ServerError("Transfer canceled", this).createEx();
            case ABORTED:
                throw new ServerError("Transfer aborted", this).createEx();
            case FAILED:
                throw ServerError.createEx(status.getErrorDesc(), ErrorCode.FATAL);
            default:
                return; // active transfer
        }
    }
}
