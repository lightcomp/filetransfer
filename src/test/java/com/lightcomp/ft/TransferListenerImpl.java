package com.lightcomp.ft;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.client.TransferRequest;
import com.lightcomp.ft.client.TransferStatus;
import com.lightcomp.ft.exception.TransferException;

public class TransferListenerImpl implements TransferRequest {

    private final Logger logger = LoggerFactory.getLogger(UploadRequestImpl.class);

    @Override
    public void onTransferProgress(TransferStatus status) {
        logger.info("Sender: transfer progress, requestId={}, detail: {}", requestId, status);
    }

    @Override
    public void onTransferSuccess() {
        logger.info("Sender: transfer success, requestId={}", requestId);
    }

    @Override
    public void onTransferCanceled() {
        logger.warn("Sender: transfer canceled, requestId={}", requestId);
    }

    @Override
    public void onTransferFailed(TransferException cause) {
        // logged internally
    }
}
