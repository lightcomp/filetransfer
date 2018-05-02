package com.lightcomp.ft.client.operations;

import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.wsdl.v1.FileTransferService;

public class BeginOperation implements Operation {

    private final OperationListener listener;

    private final String requestId;

    public BeginOperation(OperationListener listener, String requestId) {
        this.listener = listener;
        this.requestId = requestId;
    }

    @Override
    public boolean isInterruptible() {
        return true;
    }

    @Override
    public int getRecoveryCount() {
        return 0;
    }

    @Override
    public boolean execute(FileTransferService client) {
        if (!listener.isOperationFeasible(this)) {
            return false;
        }
        try {
            String transferId = client.begin(requestId);
            listener.onBeginSuccess(transferId);
            return true;
        } catch (Throwable t) {
            throw TransferExceptionBuilder.from("Failed to begin transfer").addParam("requestId", requestId).setCause(t).build();
        }
    }
}
