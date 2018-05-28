package com.lightcomp.ft.client.internal.operations;

import com.lightcomp.ft.client.internal.ExceptionType;
import com.lightcomp.ft.client.internal.operations.OperationStatus.Type;
import com.lightcomp.ft.core.send.SendFrameContext;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.wsdl.v1.FileTransferService;
import com.lightcomp.ft.xsd.v1.FileTransferState;
import com.lightcomp.ft.xsd.v1.Frame;
import com.lightcomp.ft.xsd.v1.SendRequest;
import com.lightcomp.ft.xsd.v1.TransferStatus;

public class SendOperation extends AbstractOperation {

    private final SendFrameContext frameCtx;

    public SendOperation(OperationHandler handler, FileTransferService servce, SendFrameContext frameCtx) {
        super(handler, servce);
        this.frameCtx = frameCtx;
    }

    @Override
    protected void send() throws FileTransferException {
        Frame frame = frameCtx.createFrame();
        SendRequest sr = new SendRequest();
        sr.setFrame(frame);
        sr.setTransferId(handler.getTransferId());
        service.send(sr);
    }

    @Override
    protected OperationStatus resolveServerStatus(TransferStatus status) {
        // check transfer state
        FileTransferState fts = status.getState();
        if (fts != FileTransferState.ACTIVE) {
            return new OperationStatus(Type.FAIL).setFailureMessage("Failed to send frame, invalid server state")
                    .addFailureParam("serverState", fts);
        }
        // check frame seq number
        int lastSeqNum = status.getLastFrameSeqNum();
        int seqNum = frameCtx.getSeqNum();
        // test if succeeded
        if (seqNum == lastSeqNum) {
            return new OperationStatus(Type.SUCCESS);
        }
        // test if match with previous frame
        if (seqNum == lastSeqNum + 1) {
            return null; // next try
        }
        // incorrect frame number
        return new OperationStatus(Type.FAIL).setFailureMessage("Cannot recover last send frame")
                .addFailureParam("seqNum", seqNum).addFailureParam("serverSeqNum", lastSeqNum);
    }

    @Override
    protected OperationStatus recoveryFailed(Type type, Throwable ex, ExceptionType exType) {
        return super.recoveryFailed(type, ex, exType).setFailureMessage("Failed to send frame").addFailureParam("seqNum",
                frameCtx.getSeqNum());
    }
}
