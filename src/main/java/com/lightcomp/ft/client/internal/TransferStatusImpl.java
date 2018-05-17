package com.lightcomp.ft.client.internal;

import java.time.LocalDateTime;

import org.apache.commons.lang3.Validate;

import com.lightcomp.ft.client.TransferState;
import com.lightcomp.ft.client.TransferStatus;

public class TransferStatusImpl implements TransferStatus {

    private TransferState state;

    private LocalDateTime lastActivity;

    private LocalDateTime startTime;

    private int retryCount;

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
        retryCount = source.retryCount;
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
    public int getRetryCount() {
        return retryCount;
    }

    @Override
    public long getTransferedSize() {
        return transferedSize;
    }

    /* modify methods */

    public void resetRetryCount() {
        retryCount = 0;
    }

    public void incrementRetryCount() {
        retryCount++;
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
                + ", retryCount=" + retryCount + ", transferedSize=" + transferedSize + "]";
    }
}
