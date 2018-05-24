package com.lightcomp.ft.server.internal;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.common.TaskExecutor;
import com.lightcomp.ft.core.TransferInfo;
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.server.ServerConfig;
import com.lightcomp.ft.server.TransferDataHandler;
import com.lightcomp.ft.server.TransferState;
import com.lightcomp.ft.server.TransferStatus;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.xsd.v1.ErrorCode;
import com.lightcomp.ft.xsd.v1.GenericDataType;

public abstract class AbstractTransfer implements Transfer, TransferInfo {

    private static final Logger logger = LoggerFactory.getLogger(AbstractTransfer.class);

    protected final TransferStatusImpl status = new TransferStatusImpl();

    protected final String transferId;

    protected final TransferDataHandler handler;

    protected final ServerConfig config;

    protected final TaskExecutor executor;

    protected AbstractTransfer(String transferId, TransferDataHandler handler, ServerConfig config,
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

    public synchronized TransferStatus getStatus() {
        return status.copy();
    }

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
        handler.onTransferProgress(ts);
    }

    @Override
    public synchronized TransferStatus getConfirmedStatus() throws FileTransferException {
        TransferState ts = status.getState();
        // terminal state can be reported immediately
        if (ts.isTerminal()) {
            return status.copy();
        }
        // check if busy
        if (ts == TransferState.FINISHING || isBusy()) {
            throw new ErrorContext("Transfer is busy", this).setCode(ErrorCode.BUSY).createEx();
        }
        return status.copy();
    }

    protected abstract boolean isBusy();

    @Override
    public GenericDataType finish() throws FileTransferException {
        ErrorContext ec = null;
        synchronized (this) {
            checkActiveTransfer();
            // check transfered state
            if (status.getState() != TransferState.TRANSFERED) {
                ec = new ErrorContext("Not all frames were transfered", this);
                // state must be changed in same sync block
                status.changeStateToFailed(ec.getDesc());
                // notify canceling threads
                notifyAll();
            } else {
                status.changeState(TransferState.FINISHING);
            }
        }
        // onTransferFailed must be called outside of sync block
        if (ec != null) {
            if (ec.isFatal()) {
                onTransferFailed(ec);
            }
            throw ec.createEx();
        }
        return finishInternal();
    }

    private GenericDataType finishInternal() throws FileTransferException {
        GenericDataType response;
        try {
            response = handler.onTransferSuccess();
        } catch (Throwable t) {
            ErrorContext ec = new ErrorContext("Success callback of data handler cause exception", this).setCause(t);
            transferFailed(ec);
            throw ec.createEx();
        }
        synchronized (this) {
            // transfer can fail during finishing only when callback throws exception
            status.changeStateToFinished(response);
            // notify canceling threads
            notifyAll();
        }
        return response;
    }

    @Override
    public synchronized void abort() throws FileTransferException {
        synchronized (this) {
            while (true) {
                waitWhileFinishing();
                // check terminal states
                TransferState ts = status.getState();
                if (ts == TransferState.FINISHED) {
                    throw new ErrorContext("Finished transfer cannot be aborted", this).createEx();
                }
                if (ts == TransferState.CANCELED || ts == TransferState.FAILED) {
                    return; // failed will be handled as canceled
                }
                status.changeState(TransferState.CANCELED);
                // notify other canceling threads
                notifyAll();
                break;
            }
        }
        handler.onTransferCanceled();
    }

    public void cancel() throws TransferException {
        synchronized (this) {
            while (true) {
                waitWhileFinishing();
                // check terminal states
                TransferState ts = status.getState();
                if (ts == TransferState.FINISHED) {
                    throw new TransferExceptionBuilder("Finished transfer cannot be canceled", this).build();
                }
                if (ts == TransferState.FAILED) {
                    throw new TransferExceptionBuilder("Failed transfer cannot be canceled", this).build();
                }
                if (ts == TransferState.CANCELED) {
                    return;
                }
                status.changeState(TransferState.CANCELED);
                // notify other canceling threads
                notifyAll();
                break;
            }
        }
        handler.onTransferCanceled();
    }

    public void terminate() {
        boolean canceled = false;
        synchronized (this) {
            waitWhileFinishing();
            // if terminal job done
            if (!status.getState().isTerminal()) {
                status.changeState(TransferState.CANCELED);
                // notify canceling threads
                notifyAll();
                // set flag for handler
                canceled = true;
            }
        }
        if (canceled) {
            try {
                handler.onTransferCanceled();
            } catch (Throwable t) {
                // exception is only logged
                new ErrorContext("Cancel callback of data handler cause exception", this).setCause(t).log(logger);
            }
        }
        clearResources();
    }

    public boolean terminateIfInactive() {
        ErrorContext ec = null;
        synchronized (this) {
            waitWhileFinishing();
            // if terminal job done
            if (!status.getState().isTerminal()) {
                // check transfer for inactive timeout
                long timeout = config.getInactiveTimeout();
                LocalDateTime timeoutLimit = LocalDateTime.now().minus(timeout, ChronoUnit.SECONDS);
                LocalDateTime lastActivity = status.getLastActivity();
                if (timeoutLimit.isBefore(lastActivity)) {
                    return false;
                }
                // transfer is inactive
                ec = new ErrorContext("Inactivity timeout reached", this);
                status.changeStateToFailed(ec.getDesc());
                // notify canceling threads
                notifyAll();
            }
        }
        if (ec != null) {
            // timeouted transfer
            onTransferFailed(ec);
        }
        clearResources();
        return true;
    }

    protected abstract void clearResources();

    /**
     * Handles transfer failure. If transfer is already terminated error is logged.
     */
    protected void transferFailed(ErrorContext ec) {
        boolean terminated = false;
        synchronized (this) {
            if (status.getState().isTerminal()) {
                terminated = true;
            } else {
                status.changeStateToFailed(ec.getDesc());
                // notify canceling threads
                notifyAll();
            }
        }
        if (terminated) {
            ec.log(logger, "Terminated trasfer thrown exception");
        } else {
            onTransferFailed(ec);
        }
    }

    /**
     * Logs error and notifies data handler about failure. Method should't be synchronized.
     */
    protected void onTransferFailed(ErrorContext ec) {
        ec.log(logger);
        try {
            handler.onTransferFailed(ec.getDesc());
        } catch (Throwable t) {
            // exception is only logged
            new ErrorContext("Fail callback of data handler cause exception", this).setCause(t).log(logger);
        }
    }

    /**
     * After check transfer can be only in STARTED or TRANSFERED state and not busy.
     */
    protected void checkActiveTransfer() throws FileTransferException {
        switch (status.getState()) {
            case CREATED:
                throw new IllegalStateException();
            case FINISHING:
                throw new ErrorContext("Transfer is busy", this).setCode(ErrorCode.BUSY).createEx();
            case FINISHED:
                throw new ErrorContext("Transfer finished", this).createEx();
            case CANCELED:
                throw new ErrorContext("Transfer canceled", this).createEx();
            case FAILED:
                throw ErrorContext.createEx(status.getErrorDesc(), ErrorCode.FATAL);
            default:
                if (isBusy()) {
                    throw new ErrorContext("Transfer is busy", this).setCode(ErrorCode.BUSY).createEx();
                }
        }
        // active transfer
    }

    /**
     * Method waits while transfer finishing, must be synchronized by caller.
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
}
