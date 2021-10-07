package com.lightcomp.ft.client;

import com.lightcomp.ft.xsd.v1.GenericDataType;

/**
 * AbstractRequest empty implementation of TransferRequest
 * Override selected methods when use  
 */
public abstract class AbstractRequest implements TransferRequest {

    private final GenericDataType data;

    protected Transfer transfer;

    private volatile boolean terminated = false;

    protected AbstractRequest(GenericDataType data) {
        this.data = data;
    }

    public boolean isTerminated() {
        return terminated;
    }

    @Override
    public GenericDataType getData() {
        return data;
    }

    @Override
    public String getLogId() {
        return null;
    }

    /**
     * Call super when override
     */
    @Override
    public void onTransferInitialized(Transfer transfer) {
        this.transfer = transfer;
    }

    @Override
    public void onTransferProgress(TransferStatus status) {
    }

    /**
     * Call super when override
     */
    @Override
    public void onTransferSuccess(GenericDataType response) {
        terminated = true;
    }

    /**
     * Call super when override
     */
    @Override
    public void onTransferCanceled() {
        terminated = true;
    }

    /**
     * Call super when override
     */
    @Override
    public void onTransferFailed() {
        terminated = true;
    }
}
