package com.lightcomp.ft.server.impl;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.exception.CanceledException;
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.server.ReceiverConfig;
import com.lightcomp.ft.server.TransferAcceptor;
import com.lightcomp.ft.server.TransferState;
import com.lightcomp.ft.server.TransferStatus;
import com.lightcomp.ft.server.impl.tasks.BeginTask;
import com.lightcomp.ft.server.impl.tasks.FrameTask;
import com.lightcomp.ft.server.impl.tasks.PrepareTask;
import com.lightcomp.ft.server.impl.tasks.Task;
import com.lightcomp.ft.server.impl.tasks.TransferFile;
import com.lightcomp.ft.xsd.v1.ErrorCode;
import com.lightcomp.ft.xsd.v1.FileChecksum;
import com.lightcomp.ft.xsd.v1.FileTransfer;
import com.lightcomp.ft.xsd.v1.Frame;

import cxf.FileTransferException;

public class TransferImpl implements TransferContext {

    public enum ActivityStatus {
        /**
         * Last activity doesn't reach timeout (transfer in any state).
         */
        ACTIVE,
        /**
         * Transfer activity timed out.
         */
        TIMEOUT,
        /**
         * Transfer activity timed out and transfer is already terminated.
         */
        TIMEOUT_TERMINATED
    }

    private static final Logger logger = LoggerFactory.getLogger(TransferImpl.class);

    private final Map<String, TransferFile> fileIdMap = new HashMap<>();

    private final TransferStatusImpl status = new TransferStatusImpl();

    private final Executor executor = Executors.newSingleThreadExecutor();

    private final TransferAcceptor acceptor;

    private final String requestId;

    private final ReceiverConfig config;

    // flag if cancel was requested
    private volatile boolean cancelRequested;

    private TransferWorker currentWorker;

    public TransferImpl(TransferAcceptor acceptor, String requestId, ReceiverConfig config) {
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
    public Path getTransferDir() {
        return acceptor.getTransferDir();
    }

    @Override
    public boolean isCancelRequested() {
        return cancelRequested;
    }

    public synchronized TransferStatusImpl getStatus() {
        return status.copy();
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

    @Override
    public int getFileCount() {
        return fileIdMap.size();
    }

    @Override
    public void addFile(TransferFile file) {
        String fileId = Validate.notEmpty(file.getFileId());
        if (fileIdMap.putIfAbsent(fileId, file) != null) {
            throw TransferExceptionBuilder.from("Duplicate file id").addParam("fileId", fileId).setTransfer(this).build();
        }
        synchronized (this) {
            status.addTotalTransferSize(file.getSize());
        }
    }

    @Override
    public TransferFile getFile(String fileId) {
        TransferFile file = fileIdMap.get(fileId);
        if (file == null) {
            throw TransferExceptionBuilder.from("File not found").addParam("fileId", fileId).setTransfer(this).build();
        }
        return file;
    }

    public void workerFinished(TransferState nextState) throws CanceledException {
        TransferStatus ts = null;
        synchronized (this) {
            // reset current worker
            Validate.notNull(currentWorker);
            currentWorker = null;
            // update current state
            status.changeState(nextState);
            // not committed transfers can be canceled
            if (nextState != TransferState.COMMITTED) {
                if (cancelRequested) {
                    throw new CanceledException();
                }
                // copy status in synch block
                ts = status.copy();
            }
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
            canceled = cancelRequested && cause instanceof CanceledException;
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
        Task beginTask = new BeginTask(fileTransfer, this);
        setTransferWorker(beginTask, TransferState.STARTED, TransferState.INITIALIZED);
        executor.execute(currentWorker);
    }

    public synchronized void process(Frame frame) throws FileTransferException {
        Task frameTask = new FrameTask(frame, this);
        setTransferWorker(frameTask, TransferState.TRANSFERING, TransferState.STARTED, TransferState.TRANSFERING);
        currentWorker.run();
        // we must throw exception if failed/canceled for synch method
        checkAbnormalTermination();
    }

    public synchronized void prepare(Collection<FileChecksum> fileChecksums) throws FileTransferException {
        Task prepareTask = new PrepareTask(fileChecksums, this);
        setTransferWorker(prepareTask, TransferState.PREPARED, TransferState.TRANSFERING);
        executor.execute(currentWorker);
    }

    public synchronized void commit() throws FileTransferException {
        setTransferWorker(null, TransferState.COMMITTED, TransferState.PREPARED);
        executor.execute(acceptor::onTransferSuccess);
    }

    /**
     * Sets current transfer worker. Transfer state needs to be checked first.
     * Caller must ensure synchronization.
     */
    private void setTransferWorker(Task task, TransferState nextState, TransferState... acceptableStates)
            throws FileTransferException {
        if (currentWorker != null) {
            throw TransferExceptionBuilder.from("Receiver is busy").setTransfer(this).setCode(ErrorCode.BUSY).buildFault();
        }
        // check if transfer failed/canceled
        checkAbnormalTermination();
        // check if current state is acceptable
        boolean validState = ArrayUtils.contains(acceptableStates, status.getState());
        if (!validState) {
            throw TransferExceptionBuilder.from("Receiver cannot proceed to next state").setTransfer(this)
                    .addParam("currentState", status.getState()).addParam("nextState", nextState).setCode(ErrorCode.FATAL)
                    .buildFault();
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

    public synchronized ActivityStatus getActivityStatus() {
        LocalDateTime last = status.getLastActivity();
        int timeout = config.getInactiveTimeout();
        LocalDateTime limit = LocalDateTime.now().minus(timeout, ChronoUnit.SECONDS);
        if (!limit.isAfter(last)) {
            return ActivityStatus.ACTIVE;
        }
        TransferState ts = status.getState();
        if (ts.ordinal() >= TransferState.COMMITTED.ordinal()) {
            return ActivityStatus.TIMEOUT_TERMINATED;
        }
        return ActivityStatus.TIMEOUT;

    }

    public synchronized void abort() throws FileTransferException {
        cancelRequested = true;

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
        cancelRequested = true;

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
