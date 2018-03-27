package com.lightcomp.ft.sender.impl.phase.operation;

import java.util.Objects;

import com.lightcomp.ft.exception.CanceledException;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.sender.impl.phase.DataPhase;
import com.lightcomp.ft.sender.impl.phase.frame.FrameContext;
import com.lightcomp.ft.xsd.v1.FileTransferState;
import com.lightcomp.ft.xsd.v1.FileTransferStatus;
import com.lightcomp.ft.xsd.v1.Frame;

import cxf.FileTransferException;
import cxf.FileTransferService;

public class SendOperation implements Operation {

    private final DataPhase dataPhase;

    private final FrameContext frameCtx;

    public SendOperation(DataPhase dataPhase, FrameContext frameCtx) {
        this.dataPhase = dataPhase;
        this.frameCtx = frameCtx;
    }

    @Override
    public void send() throws FileTransferException, CanceledException {
        FileTransferService service = dataPhase.getService();
        String transferId = dataPhase.getTransferInfo().getTransferId();

        Frame frame = frameCtx.createFrame();
        service.send(frame, transferId);
        dataPhase.onSendSuccess(frameCtx);
    }

    @Override
    public Operation createRetry() {
        return new RetryOperation(dataPhase, this) {
            @Override
            protected boolean canContinue(FileTransferStatus status) {
                // check transfer state
                FileTransferState state = status.getState();
                if (state != FileTransferState.ACTIVE) {
                    throw TransferExceptionBuilder.from("Failed to recover send operation").addParam("state", state).build();
                }

                // test if succeeded
                String lastRecievedFrameId = status.getLastReceivedFrameId();
                if (frameCtx.getFrameId().equals(lastRecievedFrameId)) {
                    dataPhase.onSendSuccess(frameCtx);
                    return false;
                }

                // test if match with last sent frame
                String lastSentFrameId = dataPhase.getLastSentFrameId();
                if (Objects.equals(lastSentFrameId, lastRecievedFrameId)) {
                    return true;
                }

                // any other frameId is exception
                throw TransferExceptionBuilder.from("Failed to recover send operation")
                        .addParam("lastSentFrameId", lastSentFrameId).addParam("lastRecievedFrameId", lastRecievedFrameId)
                        .build();
            }
        };
    }

}
