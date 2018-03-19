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

    private final TransferRequest request;

    private final SenderConfig senderConfig;

    private final FileTransferService service;

    private final TransferStatusImpl status = new TransferStatusImpl();

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
    public synchronized TransferStatus getStatus() {
        return status.copy();
    }

    @Override
    public synchronized boolean isCanceled() {
        return status.getState().equals(TransferState.CANCELED);
    }

    @Override
    public synchronized void cancel() {
        // if prepared wait until transfer thread commits or fails
        while (status.getState().equals(TransferState.PREPARED)) {
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        if (status.getState().equals(TransferState.COMMITTED)) {
            throw TransferExceptionBuilder.from("Committed transfer cannot be canceled").setTransfer(this).build();
        }
        // do not override state if already failed
        if (!status.getState().equals(TransferState.FAILED)) {
            status.changeState(TransferState.CANCELED);
        }
        // notify transfer thread if sleeps for recovery
        notify();
    }

    @Override
    public synchronized void onFilePrepared(long size) {
        if (isCanceled()) {
            return; // ignore callback, allow phase to cancel
        }
        status.addTotalTransferSize(size);
    }

    @Override
    public void onDataSent(long size) {
        TransferStatus ts;
        synchronized (this) {
            if (isCanceled()) {
                return; // ignore callback, allow phase to cancel
            }
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
            if (isCanceled()) {
                return; // ignore callback, allow phase to cancel
            }
            status.incrementRecoveryCount();
            // copy status in synch block
            ts = status.copy();
        }
        request.onTransferProgress(ts);
        sleepForRecovery();
    }

    private synchronized void sleepForRecovery() {
        long ms = senderConfig.getRecoveryDelay() * 1000;
        try {
            // no need to wait if canceled
            if (!isCanceled()) {
                wait(ms);
            }
        } catch (InterruptedException e) {
            // by default handled as transfer failure
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        try {
            Phase phase = new BeginPhase(this);
            while (phase != null) {
                phase.process();
                phase = prepareNextPhase(phase);
            }
            request.onTransferSuccess();
        } catch (CanceledException ce) {
            handleCancellation();
        } catch (TransferException te) {
            handleFailure(te);
        } catch (Throwable t) {
            TransferException te = TransferExceptionBuilder.from("File transfer failed").setTransfer(this).setCause(t).build();
            handleFailure(te);
        }
    }

    private Phase prepareNextPhase(Phase phase) throws CanceledException {
        TransferStatus ts;
        synchronized (this) {
            if (isCanceled()) {
                throw new CanceledException();
            }
            status.changeState(phase.getNextState());
            // copy status in synch block
            ts = status.copy();
            // notify canceling thread about status
            notify();
        }
        request.onTransferProgress(ts);
        return phase.getNextPhase();
    }

    private void handleFailure(TransferException te) {
        boolean canceled;
        synchronized (this) {
            canceled = isCanceled();
            // do not override state if already canceled
            if (!canceled) {
                status.changeState(TransferState.FAILED);
                // notify canceling thread about status
                notify();
            }
        }
        logger.error(te.getMessage(), te);
        sendAbort();
        if (canceled) {
            request.onTransferCanceled();
        } else {
            request.onTransferFailed(te);
        }
    }

    private void handleCancellation() {
        // in current impl isCancel() is only source of cancellation
        Validate.isTrue(isCanceled());
        sendAbort();
        request.onTransferCanceled();
    }

    private void sendAbort() {
        try {
            service.abort(transferId);
        } catch (Throwable t) {
            TransferException te = TransferExceptionBuilder.from("Unable to abort transfer").setTransfer(this).setCause(t).build();
            logger.error(te.getMessage(), te.getCause());
        }
    }
}
