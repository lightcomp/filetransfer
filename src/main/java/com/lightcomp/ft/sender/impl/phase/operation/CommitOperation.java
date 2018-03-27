package com.lightcomp.ft.sender.impl.phase.operation;

import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.sender.impl.phase.CommitPhase;
import com.lightcomp.ft.xsd.v1.FileTransferStatus;

import cxf.FileTransferException;
import cxf.FileTransferService;

public class CommitOperation implements Operation {

    private final CommitPhase commitPhase;

    public CommitOperation(CommitPhase commitPhase) {
        this.commitPhase = commitPhase;
    }

    @Override
    public void send() throws FileTransferException {
        FileTransferService service = commitPhase.getService();
        String transferId = commitPhase.getTransferInfo().getTransferId();

        service.commit(transferId);
    }

    @Override
    public Operation createRetry() {
        return new RetryOperation(commitPhase, this) {
            @Override
            protected boolean canContinue(FileTransferStatus status) {
                switch (status.getState()) {
                    case PREPARED:
                        return true; // retry
                    case COMMITTED:
                        return false; // success
                    default:
                        throw TransferExceptionBuilder.from("Failed to recover commit operation")
                                .addParam("state", status.getState()).build();
                }
            }
        };
    }
}
