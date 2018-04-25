package com.lightcomp.ft.server.internal;

import java.time.LocalDateTime;

import org.apache.commons.lang3.Validate;

import com.lightcomp.ft.server.TransferState;
import com.lightcomp.ft.server.TransferStatus;

public class TransferStatusImpl implements TransferStatus {

    private TransferState state;

    private LocalDateTime lastActivity;

    private LocalDateTime startTime;

    private long transferedSize;

    private int lastFrameSeqNum;

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
        lastActivity = source.lastActivity;
        startTime = source.startTime;
        transferedSize = source.transferedSize;
        lastFrameSeqNum = source.lastFrameSeqNum;
        failureCause = source.failureCause;
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
    public long getTransferedSize() {
        return transferedSize;
    }

    public int getLastFrameSeqNum() {
        return lastFrameSeqNum;
    }

    public Throwable getFailureCause() {
        return failureCause;
    }

    /* modify methods */

    public void addTransferedFrame(int seqNum, long size) {
        Validate.isTrue(seqNum > 0);
        Validate.isTrue(size >= 0);

        lastFrameSeqNum = seqNum;
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
        return "TransferStatusImpl [state=" + state + ", lastActivity=" + lastActivity + ", startTime=" + startTime
                + ", transferedSize=" + transferedSize + ", lastFrameSeqNum=" + lastFrameSeqNum + ", failureCause=" + failureCause
                + "]";
    }
}
