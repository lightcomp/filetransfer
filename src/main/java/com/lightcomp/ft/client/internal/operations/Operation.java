package com.lightcomp.ft.client.internal.operations;

import com.lightcomp.ft.exception.CanceledException;
import com.lightcomp.ft.exception.TransferExceptionBuilder;

import cxf.FileTransferException;

public interface Operation {

    void send() throws FileTransferException, CanceledException;

    TransferExceptionBuilder createExceptionBuilder();

    Operation createRetryOperation();
}