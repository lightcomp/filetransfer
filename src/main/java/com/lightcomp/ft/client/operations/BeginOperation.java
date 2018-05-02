package com.lightcomp.ft.client.operations;

import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.wsdl.v1.FileTransferService;

public class BeginOperation implements Operation {

    private final OperationListener acceptor;

    private final String requestId;

    public BeginOperation(OperationListener acceptor, String requestId) {
        this.acceptor = acceptor;
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
        if (!acceptor.isOperationFeasible(this)) {
            return false;
        }
        try {
            String transferId = client.begin(requestId);
            acceptor.onBeginSuccess(transferId);
            return true;
        } catch (Throwable t) {
            throw TransferExceptionBuilder.from("Failed to begin transfer").addParam("requestId", requestId).setCause(t).build();
        }
    }
}
