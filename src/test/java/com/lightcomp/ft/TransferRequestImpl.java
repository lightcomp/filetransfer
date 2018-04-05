package com.lightcomp.ft;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.client.SourceItem;
import com.lightcomp.ft.client.TransferRequest;
import com.lightcomp.ft.client.TransferStatus;
import com.lightcomp.ft.common.ChecksumType;
import com.lightcomp.ft.exception.TransferException;

public class TransferRequestImpl implements TransferRequest {

    private final Logger logger = LoggerFactory.getLogger(TransferRequestImpl.class);

    private final String requestId;

    private final ChecksumType checksumType;

    private final Collection<SourceItem> sourceItems;

    public TransferRequestImpl(String requestId, ChecksumType checksumType, Collection<SourceItem> sourceItems) {
        this.requestId = requestId;
        this.checksumType = checksumType;
        this.sourceItems = sourceItems;
    }

    @Override
    public String getRequestId() {
        return requestId;
    }

    @Override
    public ChecksumType getChecksumType() {
        return checksumType;
    }

    @Override
    public Collection<SourceItem> getSourceItems() {
        return sourceItems;
    }

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
