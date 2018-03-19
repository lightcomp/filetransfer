package com.lightcomp.ft.receiver.impl;

import java.time.LocalDateTime;

import org.apache.commons.lang3.Validate;

import com.lightcomp.ft.receiver.TransferState;
import com.lightcomp.ft.receiver.TransferStatus;

public class TransferStatusImpl implements TransferStatus {

    private TransferState state;

    private LocalDateTime startTime;

    private LocalDateTime lastTransferTime;

    private long totalTransferSize;

    private long transferedSize;

    public TransferStatusImpl() {
        state = TransferState.INITIALIZED;
    }

    /**
     * Copy constructor.
     */
    private TransferStatusImpl(TransferStatusImpl source) {
        state = source.state;
        startTime = source.startTime;
        lastTransferTime = source.lastTransferTime;
        totalTransferSize = source.totalTransferSize;
        transferedSize = source.transferedSize;
    }

    @Override
    public TransferState getState() {
        return state;
    }

    @Override
    public LocalDateTime getStartTime() {
        return startTime;
    }

    @Override
    public LocalDateTime getLastTransferTime() {
        return lastTransferTime;
    }

    @Override
    public long getTotalTransferSize() {
        return totalTransferSize;
    }

    @Override
    public long getTransferedSize() {
        return transferedSize;
    }

    public void addTotalTransferSize(long size) {
        Validate.isTrue(size >= 0);

        totalTransferSize += size;
    }

    public void addTransferedSize(long size) {
        Validate.isTrue(size >= 0);

        transferedSize += size;
        lastTransferTime = LocalDateTime.now();
    }

    public void changeState(TransferState nextState) {
        Validate.notNull(nextState);

        state = nextState;

        if (state == TransferState.FAILED) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        lastTransferTime = now;

        if (state == TransferState.STARTED) {
            startTime = now;
        }
    }

    public TransferStatusImpl copy() {
        return new TransferStatusImpl(this);
    }
}
