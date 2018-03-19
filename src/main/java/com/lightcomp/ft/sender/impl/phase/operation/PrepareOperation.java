package com.lightcomp.ft.sender.impl.phase.operation;

import com.lightcomp.ft.exception.CanceledException;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.sender.impl.phase.PreparePhase;
import com.lightcomp.ft.xsd.v1.FileChecksums;
import com.lightcomp.ft.xsd.v1.FileTransferStatus;

import cxf.FileTransferException;
import cxf.FileTransferService;

public class PrepareOperation implements Operation {

    private final PreparePhase preparePhase;

    public PrepareOperation(PreparePhase preparePhase) {
        this.preparePhase = preparePhase;
    }

    @Override
    public void send() throws FileTransferException, CanceledException {
        FileTransferService service = preparePhase.getService();
        String transferId = preparePhase.getTransferInfo().getTransferId();

        FileChecksums fileChecksums = preparePhase.createFileChecksums();
        service.prepare(transferId, fileChecksums);
    }

    @Override
    public Operation createRetry() {
        return new RetryOperation(preparePhase, this) {
            @Override
            protected boolean canContinue(FileTransferStatus status) {
                switch (status.getState()) {
                    case ACTIVE:
                        return true; // retry
                    case PREPARED:
                        return false; // success
                    default:
                        throw TransferExceptionBuilder.from("Cannot send prepare operation").setTransfer(preparePhase.getTransferInfo())
                                .addParam("recieverState", status.getState()).build();
                }
            }
        };
    }
}