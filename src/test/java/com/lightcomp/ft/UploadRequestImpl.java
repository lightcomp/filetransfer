package com.lightcomp.ft;

import java.util.Collection;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.client.TransferStatus;
import com.lightcomp.ft.client.UploadRequest;
import com.lightcomp.ft.core.send.items.SourceItem;

public class UploadRequestImpl implements UploadRequest {

    private final Logger logger = LoggerFactory.getLogger(UploadRequestImpl.class);

    private final String requestId;

    private final Collection<SourceItem> items;

    private volatile String failureMsg;

    private volatile boolean finished;

    public UploadRequestImpl(String requestId, Collection<SourceItem> items) {
        this.requestId = requestId;
        this.items = items;
    }

    public boolean isFinished() {
        if (failureMsg != null) {
            throw new RuntimeException(failureMsg);
        }
        return finished;
    }

    @Override
    public String getRequestId() {
        return requestId;
    }

    @Override
    public Iterator<SourceItem> getItemIterator() {
        return items.iterator();
    }

    @Override
    public void onTransferProgress(TransferStatus status) {
        logger.info("Sender: transfer progress, requestId={}, detail: {}", requestId, status);
    }

    @Override
    public void onTransferSuccess() {
        logger.info("Sender: transfer success, requestId={}", requestId);
        finished = true;
    }

    @Override
    public void onTransferCanceled() {
        failureMsg = "Request: transfer canceled, requestId=" + requestId;
    }

    @Override
    public void onTransferFailed(Throwable cause) {
        failureMsg = "Request: transfer failed, requestId=" + requestId + ", detail=" + cause.getMessage();
    }
}
