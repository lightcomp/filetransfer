package com.lightcomp.ft.server.internal;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.exception.FileTransferExceptionBuilder;
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.server.TransferAcceptor;
import com.lightcomp.ft.server.TransferState;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.xsd.v1.ErrorCode;
import com.lightcomp.ft.xsd.v1.GenericData;

public abstract class AbstractTransfer implements Transfer {

    private static final Logger logger = LoggerFactory.getLogger(AbstractTransfer.class);

    protected final TransferStatusImpl status = new TransferStatusImpl();

    protected final TransferAcceptor acceptor;

    protected final int inactiveTimeout;

    protected AbstractTransfer(TransferAcceptor acceptor, int inactiveTimeout) {
        this.acceptor = acceptor;
        this.inactiveTimeout = inactiveTimeout;
    }

    @Override
    public String getTransferId() {
        return acceptor.getTransferId();
    }

    public TransferAcceptor getAcceptor() {
        return acceptor;
    }

    public synchronized boolean isBusy() {
        return isFinishing();
    }

    public synchronized boolean isFinishing() {
        return status.getState() == TransferState.FINISHING;
    }

    public synchronized TransferStatusImpl getStatus() {
        TransferStatusImpl ts = status.copy();
        // busy is set only to this copy because only this method reveals status impl
        // internal logic must read busy through isBusy getter
        ts.setBusy(isBusy());
        return ts;
    }

    @Override
    public GenericData finish() throws FileTransferException {
        synchronized (this) {
            // check if transfer is busy
            if (isFinishing()) {
                throw FileTransferExceptionBuilder.from("Transfer is busy", acceptor).setCode(ErrorCode.BUSY).build();
            }
            // check if called in valid state
            TransferState ts = status.getState();
            if (ts != TransferState.TRANSFERED) {
                throw FileTransferExceptionBuilder.from("Unable to finish transfer", acceptor)
                        .addParam("currentState", ts.toExternal()).setCause(status.getFailureCause()).build();
            }
            // change state to finishing
            status.changeState(TransferState.FINISHING);
        }
        GenericData response;
        try {
            response = acceptor.onTransferSuccess();
        } catch (Throwable t) {
            synchronized (this) {
                status.changeStateToFailed(t);
            }
            throw FileTransferExceptionBuilder.from("Acceptor callback cause error during finish", acceptor).setCause(t).build();
        }
        synchronized (this) {
            status.changeStateToFinished(response);
        }
        return response;
    }

    @Override
    public synchronized void abort() throws FileTransferException {
        synchronized (this) {
            TransferState ts = status.getState();
            if (ts == TransferState.FINISHED) {
                throw FileTransferExceptionBuilder.from("Finished transfer cannot be aborted", acceptor).build();
            }
            if (ts == TransferState.CANCELED || ts == TransferState.FAILED) {
                return; // failed will be handled as canceled
            }
            // update current state
            status.changeState(TransferState.CANCELED);
        }
        acceptor.onTransferCanceled();
    }

    public void cancel() {
        synchronized (this) {
            TransferState ts = status.getState();
            if (ts == TransferState.FINISHED) {
                throw TransferExceptionBuilder.from("Finished transfer cannot be canceled", acceptor).build();
            }
            if (ts == TransferState.FAILED) {
                throw TransferExceptionBuilder.from("Failed transfer cannot be canceled", acceptor).build();
            }
            if (ts == TransferState.CANCELED) {
                return;
            }
            // update current state
            status.changeState(TransferState.CANCELED);
        }
        acceptor.onTransferCanceled();
    }

    public void terminate() {
        TransferException failureCause = null;
        synchronized (this) {
            if (!status.getState().isTerminal()) {
                failureCause = new TransferException("Transfer terminated");
                status.changeStateToFailed(failureCause);
            }
        }
        if (failureCause != null) {
            try {
                acceptor.onTransferFailed(failureCause);
            } catch (Throwable t) {
                // exception is only logged
                TransferExceptionBuilder.from("Acceptor callback cause error during terminate", acceptor).setCause(t).log(logger);
            }
        }
        clearResources();
    }

    public boolean terminateIfInactive() {
        TransferException failureCause = null;
        synchronized (this) {
            if (!status.getState().isTerminal()) {
                // test for inactive transfer
                LocalDateTime timeoutLimit = LocalDateTime.now().minus(inactiveTimeout, ChronoUnit.SECONDS);
                LocalDateTime lastActivity = status.getLastActivity();
                if (timeoutLimit.isBefore(lastActivity)) {
                    return false; // transfer still active
                }
                // transfer inactive
                failureCause = new TransferException("Inactivity timeout reached");
                status.changeStateToFailed(failureCause);
            }
        }
        if (failureCause != null) {
            try {
                acceptor.onTransferFailed(failureCause);
            } catch (Throwable t) {
                // exception is only logged
                TransferExceptionBuilder.from("Acceptor callback cause error during terminate", acceptor).setCause(t).log(logger);
            }
        }
        clearResources();
        return true;
    }

    protected abstract void clearResources();
}
