package com.lightcomp.ft.receiver.impl;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.exception.CanceledException;
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.receiver.ReceiverConfig;
import com.lightcomp.ft.receiver.TransferAcceptor;
import com.lightcomp.ft.receiver.TransferState;
import com.lightcomp.ft.receiver.TransferStatus;
import com.lightcomp.ft.receiver.impl.tasks.BeginTask;
import com.lightcomp.ft.receiver.impl.tasks.FrameTask;
import com.lightcomp.ft.receiver.impl.tasks.PrepareTask;
import com.lightcomp.ft.receiver.impl.tasks.Task;
import com.lightcomp.ft.sender.SourceFile;
import com.lightcomp.ft.xsd.v1.ErrorCode;
import com.lightcomp.ft.xsd.v1.FileChecksum;
import com.lightcomp.ft.xsd.v1.FileTransfer;
import com.lightcomp.ft.xsd.v1.Frame;

import cxf.FileTransferException;

public class TransferImpl implements TransferContext {

    private static final Logger logger = LoggerFactory.getLogger(TransferImpl.class);

    private final TransferStatusImpl status = new TransferStatusImpl();

    private final Executor executor = Executors.newSingleThreadExecutor();

    private final TransferAcceptor acceptor;

    private final String requestId;

    private final ReceiverConfig receiverConfig;

    private volatile boolean cancelPending;

    private TransferWorker currentWorker;

    public TransferImpl(TransferAcceptor acceptor, String requestId, ReceiverConfig receiverConfig) {
        this.acceptor = acceptor;
        this.requestId = requestId;
        this.receiverConfig = receiverConfig;
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
    public boolean isCancelPending() {
        return cancelPending;
    }

    public synchronized TransferStatusImpl getStatus() {
        return status.copy();
    }

    @Override
    public synchronized void onSourceFileProcessed(SourceFile sourceFile) {
        status.addTotalTransferSize(sourceFile.getSize());
    }

    @Override
    public void onFrameSent(String frameId, long size) {
        TransferStatus ts;
        synchronized (this) {
            status.addTransferedFrame(frameId, size);
            // copy status in synch block
            ts = status.copy();
        }
        acceptor.onTransferProgress(ts);
    }

    public void workerFinished(TransferState nextState) throws CanceledException {
        TransferStatus ts = null;
        synchronized (this) {
            // reset current worker
            Validate.notNull(currentWorker);
            currentWorker = null;
            // not committed transfers can be canceled
            if (nextState != TransferState.COMMITTED) {
                if (cancelPending) {
                    throw new CanceledException();
                }
                // copy status in synch block
                ts = status.copy();
            }
            status.changeState(nextState);
            // notify canceling threads
            notifyAll();
        }
        if (nextState != TransferState.COMMITTED) {
            acceptor.onTransferProgress(ts);
        } else {
            acceptor.onTransferSuccess();
        }
    }

    public void workerFailed(Throwable cause) {
        boolean canceled;
        synchronized (this) {
            // reset current worker
            Validate.notNull(currentWorker);
            currentWorker = null;
            // cancel when canceled exception is thrown and cancel is pending
            canceled = cancelPending && cause instanceof CanceledException;
            if (canceled) {
                status.changeState(TransferState.CANCELED);
            } else {
                status.changeStateToFailed(cause);
            }
            // notify canceling threads
            notifyAll();
        }
        if (canceled) {
            acceptor.onTransferCanceled();
        } else {
            TransferException te = TransferExceptionBuilder.from("Transfer failed").setTransfer(this).setCause(cause).build();
            logger.error(te.getMessage(), te.getCause());
            acceptor.onTransferFailed(te);
        }
    }

    public synchronized void begin(FileTransfer fileTransfer) throws FileTransferException {
        Task beginTask = new BeginTask(fileTransfer);
        setTransferWorker(beginTask, TransferState.STARTED);
        executor.execute(currentWorker);
    }

    public synchronized void process(Frame frame) throws FileTransferException {
        Task frameTask = new FrameTask(frame);
        setTransferWorker(frameTask, TransferState.STARTED);
        currentWorker.run();
        // we must throw exception if failed/canceled for synch method
        checkAbnormalTermination();
    }

    public synchronized void prepare(Collection<FileChecksum> fileChecksums) throws FileTransferException {
        Task prepareTask = new PrepareTask(fileChecksums);
        setTransferWorker(prepareTask, TransferState.PREPARED);
        executor.execute(currentWorker);
    }

    public synchronized void commit() throws FileTransferException {
        setTransferWorker(null, TransferState.COMMITTED);
        executor.execute(acceptor::onTransferSuccess);
    }

    /**
     * Sets current transfer worker. Transfer state needs to be checked first.
     * Caller must ensure synchronization.
     */
    private void setTransferWorker(Task task, TransferState nextState) throws FileTransferException {
        if (currentWorker != null) {
            throw TransferExceptionBuilder.from("Receiver is busy").setTransfer(this).setCode(ErrorCode.BUSY).buildFault();
        }
        // check if transfer failed/canceled
        checkAbnormalTermination();
        // we are expecting linear progression
        TransferState ts = status.getState();
        if (nextState.ordinal() != ts.ordinal() + 1) {
            throw TransferExceptionBuilder.from("Receiver cannot process request").setTransfer(this).addParam("receiverState", ts)
                    .addParam("requestState", nextState).setCode(ErrorCode.FATAL).buildFault();
        }
        // initialize worker
        if (task != null) {
            currentWorker = new TransferWorker(this, task, nextState);
        }
    }

    /**
     * Check throws exception if transfer terminated abnormally (canceled/failed).
     * Caller must ensure synchronization.
     */
    private void checkAbnormalTermination() throws FileTransferException {
        TransferState ts = status.getState();
        if (ts == TransferState.FAILED) {
            throw TransferExceptionBuilder.from("Transfer failed").setTransfer(this).setCause(status.getFailureCause())
                    .setCode(ErrorCode.FATAL).buildFault();
        }
        if (ts == TransferState.CANCELED) {
            throw TransferExceptionBuilder.from("Transfer aborted").setTransfer(this).buildFault();
        }
    }

    public synchronized boolean isInactive() {
        LocalDateTime last = status.getLastTransferTime();
        int timeout = receiverConfig.getInactiveTimeout();
        LocalDateTime limit = LocalDateTime.now().minus(timeout, ChronoUnit.SECONDS);
        if (limit.isAfter(last)) {
            cancelPending = true;
            return true;
        }
        return false;
    }

    public synchronized void abort() throws FileTransferException {
        cancelPending = true;

        TransferState ts = status.getState();
        while (!ts.equals(TransferState.CANCELED)) {
            if (ts == TransferState.COMMITTED) {
                throw TransferExceptionBuilder.from("Commited transfer cannot be aborted").setTransfer(this)
                        .setCode(ErrorCode.FATAL).buildFault();
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
        cancelPending = true;

        TransferState ts = status.getState();
        while (!ts.equals(TransferState.CANCELED)) {
            if (ts == TransferState.COMMITTED) {
                throw TransferExceptionBuilder.from("Commited transfer cannot be canceled").setTransfer(this).build();
            }
            if (ts == TransferState.FAILED) {
                throw TransferExceptionBuilder.from("Failed transfer cannot be canceled").setTransfer(this).build();
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
}
