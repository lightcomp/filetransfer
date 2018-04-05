package com.lightcomp.ft.client.internal.operations;

import org.apache.commons.lang3.Validate;

import com.lightcomp.ft.exception.CanceledException;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.xsd.v1.FileTransferState;
import com.lightcomp.ft.xsd.v1.FileTransferStatus;

import cxf.FileTransferException;
import cxf.FileTransferService;

public class FinishOperation implements Operation {

    private final FileTransferService service;

    private final String transferId;

    public FinishOperation(FileTransferService service, String transferId) {
        this.service = service;
        this.transferId = Validate.notEmpty(transferId);
    }

    @Override
    public void send() throws FileTransferException, CanceledException {
        service.finish(transferId);
    }

    @Override
    public TransferExceptionBuilder createExceptionBuilder() {
        return TransferExceptionBuilder.from("Failed to finish transfer");
    }

    @Override
    public Operation createRetryOperation() {
        return new RetryOperation(this, transferId, service) {
            @Override
            protected boolean canContinue(FileTransferStatus status) {
                FileTransferState fts = status.getState();
                if (fts == FileTransferState.ACTIVE) {
                    return true; // next try
                }
                if (fts == FileTransferState.FINISHED) {
                    return false; // success
                }
                throw new IllegalStateException("Invalid transfer state, name=" + fts);
            }
        };
    }
}