package com.lightcomp.ft.receiver.impl;

import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.apache.commons.lang3.Validate;

import com.lightcomp.ft.TransferInfo;
import com.lightcomp.ft.common.ChecksumType;
import com.lightcomp.ft.receiver.TransferAcceptor;
import com.lightcomp.ft.receiver.TransferState;
import com.lightcomp.ft.receiver.TransferStatus;
import com.lightcomp.ft.sender.SourceFile;
import com.lightcomp.ft.xsd.v1.ErrorCode;
import com.lightcomp.ft.xsd.v1.FileChecksum;
import com.lightcomp.ft.xsd.v1.FileTransferState;
import com.lightcomp.ft.xsd.v1.FileTransferStatus;
import com.lightcomp.ft.xsd.v1.Frame;
import com.lightcomp.ft.xsd.v1.Item;

import cxf.FileTransferException;

public class TransferImpl implements TransferInfo {

    private final TransferStatusImpl status = new TransferStatusImpl();

    private final Executor executor = Executors.newSingleThreadExecutor();

    private final TransferAcceptor acceptor;

    private final String requestId;

    private final ChecksumType checksumType;

    private TransferWorker currentWorker;

    public TransferImpl(TransferAcceptor acceptor, String requestId, ChecksumType checksumType) {
        this.acceptor = acceptor;
        this.requestId = requestId;
        this.checksumType = checksumType;
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
    public boolean isCanceled() {
        // TODO Auto-generated method stub
        return false;
    }

    public TransferStatus getTransferStatus() {
        return status.copy();
    }

    public FileTransferStatus createFileTransferStatus() {

        FileTransferStatus fts = new FileTransferStatus();
        fts.setLastReceivedFrameId(ts.getLastReceivedFrameId());
        fts.setState(convertState(ts.getPhase()));
        switch (phase) {
            case VALIDATED:
                return FileTransferState.PREPARED;
            case FINISHED:
                return FileTransferState.COMMITTED;
            case FAILED:
                return FileTransferState.FAILED;
            default:
                return FileTransferState.ACTIVE;
        }

        // TODO Auto-generated method stub
        return null;
    }

    public synchronized void workerFinished(TransferState nextState) {
        Validate.notNull(currentWorker);
        currentWorker = null;
        status.changePhase(nextState);
    }

    public synchronized void workerFailed(Throwable cause) {
        Validate.notNull(currentWorker);
        currentWorker = null;
        status.setFailed(cause);
    }

    public synchronized void begin(Collection<Item> items) throws FileTransferException {
        if (currentWorker != null) {
            throw FileTransferExceptionBuilder.from("Receiver is busy").setTransfer(this).setCode(ErrorCode.BUSY).build();
        }
        checkTransfer(TransferState.INITIALIZED);

        Runnable beginTask = null; // TODO: begin task impl
        currentWorker = new TransferWorker(this, beginTask, TransferState.STARTED);
        executor.execute(currentWorker);
    }

    public void process(Frame frame) throws FileTransferException {
        synchronized (this) {
            if (currentWorker != null) {
                throw FileTransferExceptionBuilder.from("Receiver is busy").setTransfer(this).setCode(ErrorCode.BUSY).build();
            }
            checkTransfer(TransferState.STARTED);

            Runnable frameTask = null; // TODO: begin task impl
            currentWorker = new TransferWorker(this, frameTask, TransferState.STARTED);
        }
        currentWorker.run();
    }

    public synchronized void prepare(Collection<FileChecksum> fileChecksums) throws FileTransferException {
        if (currentWorker != null) {
            throw FileTransferExceptionBuilder.from("Receiver is busy").setTransfer(this).setCode(ErrorCode.BUSY).build();
        }
        checkTransfer(TransferState.TRANSFERED);

        Runnable prepareTask = null; // TODO: begin task impl
        currentWorker = new TransferWorker(this, prepareTask, TransferState.PREPARED);
        executor.execute(currentWorker);
    }

    public synchronized void commit() throws FileTransferException {
        if (currentWorker != null) {
            throw FileTransferExceptionBuilder.from("Receiver is busy").setTransfer(this).setCode(ErrorCode.BUSY).build();
        }
        checkTransfer(TransferState.PREPARED);

        Runnable frameTask = () -> acceptor.onTransferSuccess();
        currentWorker = new TransferWorker(this, frameTask, TransferState.COMMITTED);
        executor.execute(currentWorker);
    }

    public synchronized void abort() throws FileTransferException {

    }

    public synchronized void cancel() {

    }

    void onSourceFileProcessed(SourceFile sourceFile);

    public void onBeginProcessed(String transferId) {

    }

    void onDataSent(long size);

    public void onDataSuccess() {
        if (status.changePhase(TransferState.TRANSFERED)) {

        }
    }

    public void onPrepareSuccess() {
        if (status.changePhase(TransferState.VALIDATED)) {
            acceptor.onTransferProgress(getStatus());
        }
    }

    private void acquireLock() throws FileTransferException {
        // if (status.isAborted()) {
        // return false;
        // }
        if (lock.tryLock()) {
            return true;
        }
        throw FileTransferExceptionBuilder.from("Receiver is busy").setTransfer(this).setCode(ErrorCode.BUSY).build();
    }

    private void checkTransfer(TransferState requestPhase) throws FileTransferException {
        TransferState phase = status.getPhase();
        if (phase == requestPhase) {
            return;
        }
        throw FileTransferExceptionBuilder.from("Request phase does not match receiver current phase").setTransfer(this)
                .setCode(ErrorCode.FATAL).addParam("requestPhase", requestPhase).addParam("receiverPhase", phase).build();
    }

    public boolean cancelIfInactive() {
        // TODO Auto-generated method stub
        return false;
    }
}
