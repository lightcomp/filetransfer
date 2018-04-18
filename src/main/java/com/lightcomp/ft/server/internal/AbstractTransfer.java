package com.lightcomp.ft.server.internal;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.apache.commons.lang3.Validate;
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
import com.lightcomp.ft.xsd.v1.ErrorCode;
import com.lightcomp.ft.xsd.v1.FileTransferState;
import com.lightcomp.ft.xsd.v1.FileTransferStatus;

import cxf.FileTransferException;

public abstract class AbstractTransfer implements Transfer, TransferInfo {

    private static final Logger logger = LoggerFactory.getLogger(AbstractTransfer.class);

    protected final TransferStatusImpl status = new TransferStatusImpl();

    protected final TransferAcceptor acceptor;

    protected final String requestId;

    protected final ServerConfig config;

    // flag if cancel was requested
    private volatile boolean cancelRequested;

    public AbstractTransfer(TransferAcceptor acceptor, String requestId, ServerConfig config) {
        this.acceptor = acceptor;
        this.requestId = requestId;
        this.config = config;
    }

    @Override
    public String getTransferId() {
        return acceptor.getTransferId();
    }

    @Override
    public String getRequestId() {
        return requestId;
    }

    @Override
    public boolean isCancelRequested() {
        return cancelRequested;
    }

    public abstract int getLastFrameSeqNum();

    @Override
    public void begin() throws FileTransferException {
        TransferStatus ts = null;
        synchronized (this) {
            TransferState currState = status.getState();
            if (currState != TransferState.INITIALIZED) {
                throw FileTransferExceptionBuilder.from(this, "Unable to begin transfer in current state")
                        .addParam("currentState", currState).setCause(status.getFailureCause()).build();
            }
            // update current state
            status.changeState(TransferState.STARTED);
            // copy state in sync block
            ts = status.copy();
            // notify canceling threads
            notifyAll();
        }
        acceptor.onTransferProgress(ts);
    }

    @Override
    public void finish() throws FileTransferException {
        synchronized (this) {
            TransferState currState = status.getState();
            if (currState == TransferState.STARTED) {
                throw FileTransferExceptionBuilder.from(this, "Transfer is busy").setCode(ErrorCode.BUSY).build();
            }
            if (currState != TransferState.TRANSFERED) {
                throw FileTransferExceptionBuilder.from(this, "Unable to finish transfer in current state")
                        .addParam("currentState", currState).setCause(status.getFailureCause()).build();
            }
            // update current state
            status.changeState(TransferState.FINISHED);
            // notify canceling threads
            notifyAll();
        }
        Thread handlerThread = new Thread(acceptor::onTransferSuccess, "FileTransfer_SuccessHandler");
        handlerThread.start();
    }

    @Override
    public synchronized FileTransferStatus getStatus() {
        FileTransferStatus fts = new FileTransferStatus();
        fts.setState(convertState(status.getState()));
        fts.setLastFrameSeqNum(getLastFrameSeqNum());
        return fts;
    }

    @Override
    public synchronized void abort() throws FileTransferException {
        cancelRequested = true;

        TransferState ts = status.getState();
        while (!ts.equals(TransferState.CANCELED)) {
            if (ts == TransferState.FINISHED) {
                throw FileTransferExceptionBuilder.from(this, "Finished transfer cannot be aborted").build();
            }
            if (ts == TransferState.FAILED) {
                return; // just let know that transfer is not active
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

    public synchronized void cancel() {
        cancelRequested = true;

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

    public synchronized ActivityStatus getActivityStatus() {
        LocalDateTime limit = LocalDateTime.now().minus(config.getInactiveTimeout(), ChronoUnit.SECONDS);
        LocalDateTime last = status.getLastActivity();
        if (!limit.isAfter(last)) {
            return ActivityStatus.ACTIVE;
        }
        TransferState ts = status.getState();
        if (ts.ordinal() >= TransferState.FINISHED.ordinal()) {
            return ActivityStatus.INACTIVE_TERMINATED;
        }
        return ActivityStatus.INACTIVE_RUNNING;
    }

    protected void transferCanceled() {
        Validate.isTrue(cancelRequested);
        synchronized (this) {
            status.changeState(TransferState.CANCELED);
            // notify canceling threads
            notifyAll();
        }
        acceptor.onTransferCanceled();
    }

    protected void transferFailed(Throwable cause) {
        synchronized (this) {
            status.changeStateToFailed(cause);
            // notify canceling threads
            notifyAll();
        }
        TransferException te = TransferExceptionBuilder.from(this, "Transfer failed").setCause(cause).build();
        logger.error(te.getMessage(), te.getCause());
        acceptor.onTransferFailed(te);
    }

    public static FileTransferState convertState(TransferState state) {
        switch (state) {
            case STARTED:
            case TRANSFERED:
                return FileTransferState.ACTIVE;
            case FINISHED:
                return FileTransferState.FINISHED;
            case FAILED:
            case CANCELED:
                return FileTransferState.FAILED;
            default:
                throw new IllegalStateException();
        }
    }
}
