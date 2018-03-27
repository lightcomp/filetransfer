package com.lightcomp.ft.sender.impl;

import java.util.Collection;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.common.ChecksumType;
import com.lightcomp.ft.exception.CanceledException;
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.sender.SenderConfig;
import com.lightcomp.ft.sender.SourceItem;
import com.lightcomp.ft.sender.Transfer;
import com.lightcomp.ft.sender.TransferRequest;
import com.lightcomp.ft.sender.TransferState;
import com.lightcomp.ft.sender.TransferStatus;
import com.lightcomp.ft.sender.impl.phase.BeginPhase;
import com.lightcomp.ft.sender.impl.phase.Phase;

import cxf.FileTransferService;

public class TransferImpl implements Runnable, Transfer, TransferContext {

    private static final Logger logger = LoggerFactory.getLogger(TransferImpl.class);

    private final TransferStatusImpl status = new TransferStatusImpl();

    private final TransferRequest request;

    private final SenderConfig senderConfig;

    private final FileTransferService service;

    // flag if cancel was requested
    private volatile boolean cancelRequested;

    private String transferId;

    public TransferImpl(TransferRequest request, SenderConfig senderConfig, FileTransferService service) {
        this.request = request;
        this.senderConfig = senderConfig;
        this.service = service;
    }

    @Override
    public String getTransferId() {
        return transferId;
    }

    @Override
    public String getRequestId() {
        return request.getRequestId();
    }

    @Override
    public ChecksumType getChecksumType() {
        return request.getChecksumType();
    }

    @Override
    public Collection<SourceItem> getSourceItems() {
        return request.getSourceItems();
    }

    @Override
    public SenderConfig getSenderConfig() {
        return senderConfig;
    }

    @Override
    public FileTransferService getService() {
        return service;
    }

    @Override
    public void setTransferId(String transferId) {
        this.transferId = Validate.notEmpty(transferId);
    }

    @Override
    public boolean isCancelRequested() {
        return cancelRequested;
    }

    @Override
    public synchronized TransferStatus getStatus() {
        return status.copy();
    }

    @Override
    public synchronized void onFilePrepared(long size) {
        status.addTotalTransferSize(size);
    }

    @Override
    public void onDataSent(long size) {
        TransferStatus ts;
        synchronized (this) {
            status.addTransferedSize(size);
            // copy status in synch block
            ts = status.copy();
        }
        request.onTransferProgress(ts);
    }

    @Override
    public void onTransferRecovery() {
        TransferStatus ts;
        synchronized (this) {
            status.incrementRecoveryCount();
            // copy status in synch block
            ts = status.copy();
        }
        request.onTransferProgress(ts);
    }

    @Override
    public synchronized void sleep(long ms, boolean cancellable) {
        if (cancelRequested && cancellable) {
            return;
        }
        try {
            if (cancellable) {
                wait(ms);
            } else {
                Thread.sleep(ms);
            }
        } catch (InterruptedException e) {
            // by default handled as transfer failure
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized void cancel() {
        if (!cancelRequested) {
            cancelRequested = true;
            // wake up transfer thread
            notifyAll();
        }
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

    @Override
    public void run() {
        try {
            Phase phase = new BeginPhase(this);
            while (phase != null) {
                phase.process();
                phase = prepareNextPhase(phase.getNextPhase(), phase.getNextState());
            }
        } catch (Throwable t) {
            handleTransferFailure(t);
        }
    }

    private Phase prepareNextPhase(Phase nextPhase, TransferState nextState) throws CanceledException {
        TransferStatus ts = null;
        synchronized (this) {
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
        if (nextState == TransferState.COMMITTED) {
            Validate.isTrue(nextPhase == null);
            request.onTransferSuccess();
            return null;
        }
        request.onTransferProgress(ts);
        return nextPhase;
    }

    private void handleTransferFailure(Throwable cause) {
        boolean canceled;
        synchronized (this) {
            // cancel when canceled exception is thrown and cancel is pending
            canceled = cancelRequested && cause instanceof CanceledException;
            if (canceled) {
                status.changeState(TransferState.CANCELED);
            } else {
                status.changeState(TransferState.FAILED);
            }
            // notify canceling threads
            notifyAll();
        }
        abortReceiver();
        if (canceled) {
            request.onTransferCanceled();
        } else {
            TransferException te = TransferExceptionBuilder.from("Transfer failed").setTransfer(this).setCause(cause).build();
            logger.error(te.getMessage(), te.getCause());
            request.onTransferFailed(te);
        }
    }

    private void abortReceiver() {
        try {
            service.abort(transferId);
        } catch (Throwable t) {
            String msg = TransferExceptionBuilder.from("Unable to abort receiver").setTransfer(this).buildMessage();
            logger.error(msg, t);
        }
    }
}
