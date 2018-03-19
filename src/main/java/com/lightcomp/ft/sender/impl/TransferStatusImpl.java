package com.lightcomp.ft.sender.impl;

import java.time.LocalDateTime;

import org.apache.commons.lang3.Validate;

import com.lightcomp.ft.sender.TransferState;
import com.lightcomp.ft.sender.TransferStatus;

public class TransferStatusImpl implements TransferStatus {

    private TransferState state;

    private LocalDateTime startTime;

    private LocalDateTime lastTransferTime;

    private int recoveryCount;

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
        recoveryCount = source.recoveryCount;
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
    public int getRecoveryCount() {
        return recoveryCount;
    }

    @Override
    public long getTotalTransferSize() {
        return totalTransferSize;
    }

    @Override
    public long getTransferedSize() {
        return transferedSize;
    }

    public void incrementRecoveryCount() {
        recoveryCount++;
    }

    public void addTotalTransferSize(long size) {
        Validate.isTrue(size >= 0);

        totalTransferSize += size;
    }

    public void addTransferedSize(long size) {
        Validate.isTrue(size >= 0);

        transferedSize += size;
        updateLastTransfer(LocalDateTime.now());
    }

    public void changeState(TransferState nextState) {
        Validate.notNull(nextState);

        state = nextState;

        if (state == TransferState.FAILED) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        updateLastTransfer(now);

        if (state == TransferState.STARTED) {
            startTime = now;
        }
    }

    public TransferStatusImpl copy() {
        return new TransferStatusImpl(this);
    }

    private void updateLastTransfer(LocalDateTime transferTime) {
        lastTransferTime = transferTime;
        recoveryCount = 0;
    }
}
