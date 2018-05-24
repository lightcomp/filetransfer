package com.lightcomp.ft.simple;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.client.Transfer;
import com.lightcomp.ft.client.TransferRequest;
import com.lightcomp.ft.client.TransferStatus;
import com.lightcomp.ft.xsd.v1.GenericDataType;

public abstract class AbstractRequest implements TransferRequest {

    private static final Logger logger = LoggerFactory.getLogger(DwnldRequestImpl.class);

    private final GenericDataType data;

    private Transfer transfer;

    private volatile boolean terminated;

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

    @Override
    public void onTransferInitialized(Transfer transfer) {
        logger.info("Client transfer initialized, transferId={}", transfer.getTransferId());
        this.transfer = transfer;
    }

    @Override
    public void onTransferProgress(TransferStatus status) {
        logger.info("Client transfer progressed, transferId={}, detail: {}", transfer.getTransferId(), status);
    }

    @Override
    public void onTransferSuccess(GenericDataType response) {
        logger.info("Client transfer succeeded, transferId={}, response: {}", transfer.getTransferId(), response);
        terminated = true;
    }

    @Override
    public void onTransferCanceled() {
        logger.info("Client transfer canceled, transferId={}", transfer.getTransferId());
        terminated = true;
    }

    @Override
    public void onTransferFailed() {
        logger.info("Client transfer failed, transferId=" + transfer.getTransferId());
        terminated = true;
    }
}
