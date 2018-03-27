package com.lightcomp.ft.receiver.impl;

import java.time.LocalDateTime;

import org.apache.commons.lang3.Validate;

import com.lightcomp.ft.receiver.TransferState;
import com.lightcomp.ft.receiver.TransferStatus;

public class TransferStatusImpl implements TransferStatus {

    private TransferState state;

    private LocalDateTime startTime;

    private LocalDateTime lastActivity;

    private long totalTransferSize;

    private long transferedSize;

    private String lastReceivedFrameId;

    private Throwable failureCause;

    public TransferStatusImpl() {
        state = TransferState.INITIALIZED;
        lastActivity = LocalDateTime.now();
    }

    /**
     * Copy constructor.
     */
    private TransferStatusImpl(TransferStatusImpl source) {
        state = source.state;
        startTime = source.startTime;
        lastActivity = source.lastActivity;
        totalTransferSize = source.totalTransferSize;
        transferedSize = source.transferedSize;
        lastReceivedFrameId = source.lastReceivedFrameId;
        failureCause = source.failureCause;
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
    public LocalDateTime getLastActivity() {
        return lastActivity;
    }

    @Override
    public long getTotalTransferSize() {
        return totalTransferSize;
    }

    @Override
    public long getTransferedSize() {
        return transferedSize;
    }

    public String getLastReceivedFrameId() {
        return lastReceivedFrameId;
    }

    public Throwable getFailureCause() {
        return failureCause;
    }

    public void addTotalTransferSize(long size) {
        Validate.isTrue(size >= 0);

        totalTransferSize += size;
    }

    public void addTransferedFrame(String frameId, long size) {
        Validate.notEmpty(frameId);
        Validate.isTrue(size >= 0);

        lastReceivedFrameId = frameId;
        transferedSize += size;
        updateActivity();
    }

    public void changeStateToFailed(Throwable cause) {
        changeState(TransferState.FAILED);
        failureCause = cause;
    }

    public void changeState(TransferState nextState) {
        Validate.notNull(nextState);

        state = nextState;
        updateActivity();

        if (startTime == null) {
            startTime = lastActivity;
        }
    }

    public TransferStatusImpl copy() {
        return new TransferStatusImpl(this);
    }

    private void updateActivity() {
        lastActivity = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "TransferStatusImpl [state=" + state + ", startTime=" + startTime + ", lastActivity=" + lastActivity
                + ", totalTransferSize=" + totalTransferSize + ", transferedSize=" + transferedSize + ", lastReceivedFrameId="
                + lastReceivedFrameId + ", failureCause=" + failureCause + "]";
    }
}
