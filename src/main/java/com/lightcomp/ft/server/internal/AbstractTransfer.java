package com.lightcomp.ft.server.internal;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.core.TransferInfo;
import com.lightcomp.ft.exception.FileTransferExceptionBuilder;
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.server.TransferAcceptor;
import com.lightcomp.ft.server.TransferState;
import com.lightcomp.ft.server.TransferStatus;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.xsd.v1.ErrorCode;

public abstract class AbstractTransfer implements Transfer, TransferInfo {

    protected static final Logger logger = LoggerFactory.getLogger(AbstractTransfer.class);

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

    public synchronized TransferStatus getStatus() {
        return status.copy();
    }

    protected abstract boolean isProcessingFrame();

    @Override
    public void finish() throws FileTransferException {
        synchronized (this) {
            TransferState ts = status.getState();
            if (ts == TransferState.STARTED && isProcessingFrame()) {
                throw FileTransferExceptionBuilder.from("Transfer is busy", this).setCode(ErrorCode.BUSY).build();
            }
            if (ts != TransferState.TRANSFERED) {
                throw FileTransferExceptionBuilder.from("Unable to finish transfer", this)
                        .addParam("currentState", TransferState.convert(ts)).setCause(status.getFailureCause()).build();
            }
            // update current state
            status.changeState(TransferState.FINISHED);
        }
        Thread handlerThread = new Thread(acceptor::onTransferSuccess, "FileTransfer_SuccessHandler");
        handlerThread.start();
    }

    @Override
    public synchronized void abort() throws FileTransferException {
        synchronized (this) {
            TransferState ts = status.getState();
            if (ts == TransferState.FINISHED) {
                throw FileTransferExceptionBuilder.from("Finished transfer cannot be aborted", this).build();
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
                throw TransferExceptionBuilder.from("Finished transfer cannot be canceled", this).build();
            }
            if (ts == TransferState.FAILED) {
                throw TransferExceptionBuilder.from("Failed transfer cannot be canceled", this).build();
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
            if (!TransferState.isTerminal(status.getState())) {
                failureCause = new TransferException("Transfer terminated");
                status.changeStateToFailed(failureCause);
            }
        }
        if (failureCause != null) {
            try {
                acceptor.onTransferFailed(failureCause);
            } catch (Throwable t) {
                // exception is only logged
                TransferExceptionBuilder.from("Acceptor callback cause error during terminate", this).setCause(t).log(logger);
            }
        }
        clearResources();
    }

    public boolean terminateIfInactive() {
        TransferException failureCause = null;
        synchronized (this) {
            if (!TransferState.isTerminal(status.getState())) {
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
                TransferExceptionBuilder.from("Acceptor callback cause error during terminate", this).setCause(t).log(logger);
            }
        }
        clearResources();
        return true;
    }

    protected abstract void clearResources();
}
