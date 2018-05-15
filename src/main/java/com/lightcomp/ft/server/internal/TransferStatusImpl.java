package com.lightcomp.ft.server.internal;

import java.time.LocalDateTime;

import org.apache.commons.lang3.Validate;

import com.lightcomp.ft.server.ErrorDesc;
import com.lightcomp.ft.server.TransferState;
import com.lightcomp.ft.server.TransferStatus;
import com.lightcomp.ft.xsd.v1.GenericData;

public class TransferStatusImpl implements TransferStatus {

    private TransferState state;

    private LocalDateTime lastActivity;

    private LocalDateTime startTime;

    private long transferedSize;

    private int lastFrameSeqNum;

    private GenericData response;

    private ErrorDesc errorDesc;

    private boolean busy;

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
        transferedSize = src.transferedSize;
        lastFrameSeqNum = src.lastFrameSeqNum;
        response = src.response;
        errorDesc = src.errorDesc;
        busy = src.busy;
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

    @Override
    public int getLastFrameSeqNum() {
        return lastFrameSeqNum;
    }

    @Override
    public GenericData getResponse() {
        return response;
    }

    @Override
    public ErrorDesc getErrorDesc() {
        return errorDesc;
    }

    public boolean isBusy() {
        return busy;
    }

    /* modify methods */

    public void addTransferedData(long size) {
        Validate.isTrue(size >= 0);

        transferedSize += size;
        updateActivity();
    }

    public void incrementFrameSeqNum() {
        lastFrameSeqNum++;
        updateActivity();
    }

    public void changeStateToFinished(GenericData response) {
        changeState(TransferState.FINISHED);
        this.response = response;
    }

    public void changeStateToFailed(ErrorDesc errorDesc) {
        changeState(TransferState.FAILED);
        this.errorDesc = errorDesc;
    }

    public void changeState(TransferState nextState) {
        Validate.notNull(nextState);

        state = nextState;
        updateActivity();
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
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
                + ", transferedSize=" + transferedSize + ", lastFrameSeqNum=" + lastFrameSeqNum + ", response=" + response
                + ", errorDesc=" + errorDesc + ", busy=" + busy + "]";
    }
}
