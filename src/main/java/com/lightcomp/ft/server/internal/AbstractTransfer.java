package com.lightcomp.ft.server.internal;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.core.TransferInfo;
import com.lightcomp.ft.exception.CanceledException;
import com.lightcomp.ft.exception.FileTransferExceptionBuilder;
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.server.ServerConfig;
import com.lightcomp.ft.server.TransferAcceptor;
import com.lightcomp.ft.server.TransferState;
import com.lightcomp.ft.server.TransferStatus;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.xsd.v1.ErrorCode;
import com.lightcomp.ft.xsd.v1.FileTransferState;
import com.lightcomp.ft.xsd.v1.FileTransferStatus;

public abstract class AbstractTransfer implements Transfer, TransferInfo {

    private static final Logger logger = LoggerFactory.getLogger(AbstractTransfer.class);

    protected final TransferStatusImpl status = new TransferStatusImpl();

    protected final TransferAcceptor acceptor;

    private final String requestId;

    private final ServerConfig config;

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

    public abstract boolean isProcessingFrame();

    @Override
    public synchronized FileTransferStatus getStatus() {
        FileTransferStatus fts = new FileTransferStatus();
        fts.setState(convertState(status.getState()));
        fts.setLastFrameSeqNum(lastFrameSeqNum);
        return fts;
    }

    @Override
    public void finish() throws FileTransferException {
        synchronized (this) {
            TransferState currState = status.getState();
            if (currState == TransferState.STARTED && isProcessingFrame()) {
                throw FileTransferExceptionBuilder.from(this, "Transfer is busy").setCode(ErrorCode.BUSY).build();
            }
            if (currState != TransferState.TRANSFERED) {
                throw FileTransferExceptionBuilder.from(this, "Unable to finish transfer").addParam("currentState", currState)
                        .setCause(status.getFailureCause()).build();
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
            if (ts == TransferState.FAILED || ts == TransferState.CANCELED) {
                return; // handle failed as canceled
            }
            // update current state
            status.changeState(TransferState.CANCELED);
        }
        acceptor.onTransferCanceled(true);
    }

    public void cancel() {
        synchronized (this) {
            TransferState ts = status.getState();
            if (ts == TransferState.FINISHED) {
                throw TransferExceptionBuilder.from(this, "Finished transfer cannot be canceled").build();
            }
            if (ts == TransferState.FAILED) {
                throw TransferExceptionBuilder.from(this, "Failed transfer cannot be canceled").build();
            }
            if (ts == TransferState.CANCELED) {
                return;
            }
            // update current state
            status.changeState(TransferState.CANCELED);
        }
        acceptor.onTransferCanceled(false);
    }

    public void onFrameFinished(int seqNum, boolean last) {
        TransferStatus ts;
        synchronized (this) {
            // check transfer state and last frame number
            Validate.isTrue(status.getState() == TransferState.STARTED);
            Validate.isTrue(status.getLastFrameSeqNum() + 1 == seqNum);
            // update progress
            status.incrementFrameSeqNum();
            // update current state if last frame
            if (last) {
                status.changeState(TransferState.TRANSFERED);
            }
            // copy status in synch block
            ts = status.copy();
        }
        acceptor.onTransferProgress(ts);
    }

    public synchronized boolean terminateIfInactive() {
        TransferState ts = status.getState();
        if (ts.ordinal() >= TransferState.FINISHED.ordinal()) {
            return ActivityStatus.INACTIVE_TERMINATED;
        }
        int timeout = config.getInactiveTimeout();
        LocalDateTime limit = LocalDateTime.now().minus(timeout, ChronoUnit.SECONDS);
        LocalDateTime last = status.getLastActivity();
        if (limit.isAfter(last)) {
            return ActivityStatus.INACTIVE_RUNNING;
        }
        return ActivityStatus.ACTIVE;

        closeResources();
    }

    public void transferFailed(Throwable cause) {
        TransferExceptionBuilder builder;
        boolean failed = false;

        synchronized (this) {
            TransferState ts = status.getState();
            if (ts == TransferState.CANCELED) {
                if (cause instanceof CanceledException) {
                    return; // ignore canceled exception if transfer was canceled
                }
                builder = TransferExceptionBuilder.from("Canceled transfer thrown exception");
            } else if (ts == TransferState.FINISHED) {
                builder = TransferExceptionBuilder.from("Finished transfer thrown exception");
            } else if (ts == TransferState.FAILED) {
                builder = TransferExceptionBuilder.from("Failed transfer thrown exception");
            } else {
                builder = TransferExceptionBuilder.from("Transfer failed");
                status.changeStateToFailed(cause);
                failed = true;
            }
        }
        TransferException te = builder.setTransfer(this).setCause(cause).build();
        logger.error(te.getMessage(), te.getCause());
        if (failed) {
            acceptor.onTransferFailed(te);
        }
    }

    protected abstract void closeResources();

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
