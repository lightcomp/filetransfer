package com.lightcomp.ft.client.internal.operations;

import com.lightcomp.ft.exception.CanceledException;
import com.lightcomp.ft.exception.TransferExceptionBuilder;

import cxf.FileTransferException;

public class ReceiveOperation implements Operation {

    @Override
    public void send() throws FileTransferException, CanceledException {
        // TODO Auto-generated method stub

    }

    @Override
    public Operation createRetryOperation() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public TransferExceptionBuilder createExceptionBuilder() {
        // TODO Auto-generated method stub
        return null;
    }
}
