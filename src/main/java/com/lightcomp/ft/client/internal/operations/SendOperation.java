package com.lightcomp.ft.client.internal.operations;

import com.lightcomp.ft.client.internal.upload.frame.FrameContext;
import com.lightcomp.ft.exception.CanceledException;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.xsd.v1.FileTransferState;
import com.lightcomp.ft.xsd.v1.FileTransferStatus;
import com.lightcomp.ft.xsd.v1.Frame;

import cxf.FileTransferException;
import cxf.FileTransferService;

public class SendOperation implements Operation {

    private final FrameContext frameCtx;

    private final String transferId;

    private final FileTransferService service;

    public SendOperation(FrameContext frameCtx, String transferId, FileTransferService service) {
        this.frameCtx = frameCtx;
        this.transferId = transferId;
        this.service = service;
    }

    @Override
    public void send() throws FileTransferException, CanceledException {
        Frame frame = frameCtx.createFrame();
        service.send(frame, transferId);
    }

    @Override
    public TransferExceptionBuilder createExceptionBuilder() {
        return TransferExceptionBuilder.from("Failed to send frame").addParam("frameSeqNum", frameCtx.getSeqNum());
    }

    @Override
    public Operation createRetryOperation() {
        return new RetryOperation(this, transferId, service) {
            @Override
            protected boolean canContinue(FileTransferStatus status) {
                // check transfer state
                FileTransferState fts = status.getState();
                if (fts != FileTransferState.ACTIVE) {
                    throw new IllegalStateException("Invalid transfer state, name=" + fts);
                }

                int serverSeqNum = status.getLastFrameSeqNum();
                int seqNum = frameCtx.getSeqNum();
                // test if succeeded
                if (seqNum == serverSeqNum) {
                    return false;
                }
                // test if match with previous frame
                if (seqNum == serverSeqNum + 1) {
                    return true;
                }

                // any other frame number is exception
                throw TransferExceptionBuilder.from("Failed to recover frame transfer").addParam("clientSeqNum", seqNum)
                        .addParam("serverSeqNum", serverSeqNum).build();
            }
        };
    }
}
