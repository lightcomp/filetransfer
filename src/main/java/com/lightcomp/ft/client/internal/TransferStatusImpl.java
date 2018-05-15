package com.lightcomp.ft.client.internal;

import java.time.LocalDateTime;

import org.apache.commons.lang3.Validate;

import com.lightcomp.ft.client.TransferState;
import com.lightcomp.ft.client.TransferStatus;

public class TransferStatusImpl implements TransferStatus {

    private TransferState state;

    private LocalDateTime lastActivity;

    private LocalDateTime startTime;

    private int recoveryCount;

    private long transferedSize;

    public TransferStatusImpl() {
        state = TransferState.CREATED;
        lastActivity = LocalDateTime.now();
    }

    /**
     * Copy constructor.
     */
    private TransferStatusImpl(TransferStatusImpl source) {
        state = source.state;
        lastActivity = source.lastActivity;
        startTime = source.startTime;
        recoveryCount = source.recoveryCount;
        transferedSize = source.transferedSize;
    }

    @Override
    public TransferState getState() {
        return state;
    }

    @Override
    public LocalDateTime getLastActivity() {
        return lastActivity;
    }

    @Override
    public LocalDateTime getStartTime() {
        return startTime;
    }

    @Override
    public int getRecoveryCount() {
        return recoveryCount;
    }

    @Override
    public long getTransferedSize() {
        return transferedSize;
    }

    /* modify methods */

    public void resetRecoveryCount() {
        recoveryCount = 0;
    }

    public void incrementRecoveryCount() {
        recoveryCount++;
    }

    public void addTransferedData(long size) {
        Validate.isTrue(size >= 0);

        transferedSize += size;
        updateActivity();
    }

    public void changeState(TransferState nextState) {
        Validate.notNull(nextState);
        state = nextState;
        updateActivity();
    }

    public TransferStatusImpl copy() {
        return new TransferStatusImpl(this);
    }

    private void updateActivity() {
        lastActivity = LocalDateTime.now();
        if (startTime == null) {
            startTime = lastActivity;
        }
    }

    @Override
    public String toString() {
        return "TransferStatusImpl [state=" + state + ", lastActivity=" + lastActivity + ", startTime=" + startTime
                + ", recoveryCount=" + recoveryCount + ", transferedSize=" + transferedSize + "]";
    }
}
