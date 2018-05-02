package com.lightcomp.ft.server.internal;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.core.TransferInfo;
import com.lightcomp.ft.exception.FileTransferExceptionBuilder;
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.server.ServerConfig;
import com.lightcomp.ft.server.TransferAcceptor;
import com.lightcomp.ft.server.TransferState;
import com.lightcomp.ft.server.TransferStatus;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.xsd.v1.ErrorCode;

public abstract class AbstractTransfer implements Transfer, TransferInfo {

    protected static final Logger logger = LoggerFactory.getLogger(AbstractTransfer.class);

    protected final TransferStatusImpl status = new TransferStatusImpl();

    protected final TransferAcceptor acceptor;

    protected final ServerConfig serverConfig;

    protected AbstractTransfer(TransferAcceptor acceptor, ServerConfig serverConfig) {
        this.acceptor = acceptor;
        this.serverConfig = serverConfig;
    }

    @Override
    public String getTransferId() {
        return acceptor.getTransferId();
    }

    protected abstract boolean isProcessingFrame();

    protected abstract void clearResources();

    public synchronized TransferStatus getStatus() {
        return status.copy();
    }

    @Override
    public void finish() throws FileTransferException {
        synchronized (this) {
            TransferState ts = status.getState();
            if (ts == TransferState.STARTED && isProcessingFrame()) {
                throw FileTransferExceptionBuilder.from(this, "Transfer is busy").setCode(ErrorCode.BUSY).build();
            }
            if (ts != TransferState.TRANSFERED) {
                throw FileTransferExceptionBuilder.from(this, "Unable to finish transfer")
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
                throw FileTransferExceptionBuilder.from(this, "Finished transfer cannot be aborted").build();
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
            if (status.getState().ordinal() < TransferState.FINISHED.ordinal()) {
                failureCause = new TransferException("Transfer terminated");
                status.changeStateToFailed(failureCause);
            }
        }
        if (failureCause != null) {
            try {
                acceptor.onTransferFailed(failureCause);
            } catch (Throwable t) {
                TransferExceptionBuilder.from("Acceptor failure callback cause error", this).setCause(t).log(logger);
            }
        }
        clearResources();
    }

    public boolean terminateIfInactive() {
        TransferException failureCause = null;
        synchronized (this) {
            if (status.getState().ordinal() < TransferState.FINISHED.ordinal()) {
                // test for inactive transfer
                int timeout = serverConfig.getInactiveTimeout();
                LocalDateTime timeoutLimit = LocalDateTime.now().minus(timeout, ChronoUnit.SECONDS);
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
                TransferExceptionBuilder.from("Acceptor failure callback cause error", this).setCause(t).log(logger);
            }
        }
        clearResources();
        return true;
    }
}
