package com.lightcomp.ft.server.internal;

import java.time.LocalDateTime;

import org.apache.commons.lang3.Validate;

import com.lightcomp.ft.server.ErrorDesc;
import com.lightcomp.ft.server.TransferState;
import com.lightcomp.ft.server.TransferStatus;
import com.lightcomp.ft.xsd.v1.GenericDataType;

public class TransferStatusImpl implements TransferStatus {

    private TransferState state;

    private LocalDateTime lastActivity;

    private LocalDateTime startTime;

    private long transferedSize;

    private int transferedSeqNum;

    private int processedSeqNum;

    private GenericDataType response;

    private ErrorDesc errorDesc;

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
        transferedSeqNum = src.transferedSeqNum;
        processedSeqNum = src.processedSeqNum;
        response = src.response;
        errorDesc = src.errorDesc;
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
    public int getTransferedSeqNum() {
        return transferedSeqNum;
    }

    @Override
    public int getProcessedSeqNum() {
        return processedSeqNum;
    }

    @Override
    public GenericDataType getResponse() {
        return response;
    }

    @Override
    public ErrorDesc getErrorDesc() {
        return errorDesc;
    }

    /* modify methods */

    public void addTransferedData(long size) {
        Validate.isTrue(size >= 0);
        transferedSize += size;
        updateActivity();
    }

    public void incrementTransferedSeqNum() {
        transferedSeqNum++;
        updateActivity();
    }

    public void incrementProcessedSeqNum() {
        processedSeqNum++;
        updateActivity();
    }

    public void changeStateToFinished(GenericDataType response) {
        changeState(TransferState.FINISHED);
        this.response = response;
    }

    public void changeStateToFailed(ErrorDesc errorDesc) {
        changeState(TransferState.FAILED);
        this.errorDesc = errorDesc;
    }

    public void changeState(TransferState nextState) {
        state = Validate.notNull(nextState);
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
                + ", transferedSize=" + transferedSize + ", transferedSeqNum=" + transferedSeqNum + ", processedSeqNum="
                + processedSeqNum + ", response=" + response + ", errorDesc=" + errorDesc + "]";
    }
}
