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

    private int lastFrameSeqNum;

    public TransferStatusImpl() {
        state = TransferState.CREATED;
        lastActivity = LocalDateTime.now();
    }

    /**
     * Copy constructor.
     */
    private TransferStatusImpl(TransferStatusImpl src) {
        state = src.state;
        lastActivity = src.lastActivity;
        startTime = src.startTime;
        retryCount = src.retryCount;
        transferedSize = src.transferedSize;
        lastFrameSeqNum = src.lastFrameSeqNum;
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

    @Override
    public int getLastFrameSeqNum() {
        return lastFrameSeqNum;
    }

    /* modify methods */

    public void incrementRetryCount() {
        retryCount++;
    }

    public void resetRetryCount() {
        retryCount = 0;
        updateActivity();
    }
    
    public void addTransferedData(long size) {
        Validate.isTrue(size >= 0);

        transferedSize += size;
        updateActivity();
    }

    public void incrementFrameSeqNum() {
        lastFrameSeqNum++;
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
                + ", retryCount=" + retryCount + ", transferedSize=" + transferedSize + ", lastFrameSeqNum=" + lastFrameSeqNum
                + "]";
    }
}
