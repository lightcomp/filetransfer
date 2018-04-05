package com.lightcomp.ft.client.internal.operations;

import com.lightcomp.ft.exception.CanceledException;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.xsd.v1.FileTransferStatus;

import cxf.FileTransferException;
import cxf.FileTransferService;

abstract class RetryOperation implements Operation {

    private final Operation operation;

    private final String transferId;

    private FileTransferService service;

    public RetryOperation(Operation operation, String transferId, FileTransferService service) {
        this.operation = operation;
        this.transferId = transferId;
        this.service = service;
    }

    @Override
    public final void send() throws FileTransferException, CanceledException {
        // try to get current status
        FileTransferStatus status = service.status(transferId);

        // if success then resend original operation
        if (canContinue(status)) {
            operation.send();
        }
    }

    @Override
    public TransferExceptionBuilder createExceptionBuilder() {
        return operation.createExceptionBuilder();
    }

    @Override
    public final Operation createRetryOperation() {
        return this;
    }

    protected abstract boolean canContinue(FileTransferStatus status);
}
