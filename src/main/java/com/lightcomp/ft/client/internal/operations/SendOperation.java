package com.lightcomp.ft.client.internal.operations;

import com.lightcomp.ft.client.internal.upload.FrameContext;
import com.lightcomp.ft.core.TransferInfo;
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.xsd.v1.FileTransferState;
import com.lightcomp.ft.xsd.v1.FileTransferStatus;
import com.lightcomp.ft.xsd.v1.Frame;

import cxf.FileTransferException;
import cxf.FileTransferService;

public class SendOperation extends RecoverableOperation {

    private final FrameContext frameCtx;

    public SendOperation(TransferInfo transferInfo, RecoveryHandler handler, FrameContext frameCtx) {
        super(transferInfo, handler);
        this.frameCtx = frameCtx;
    }

    @Override
    protected void send(FileTransferService service) throws FileTransferException {
        Frame frame = frameCtx.createFrame();
        service.send(frame, transferInfo.getTransferId());
    }

    @Override
    protected TransferException createException(Throwable cause) {
        return TransferExceptionBuilder.from(transferInfo, "Failed to send frame").setCause(cause)
                .addParam("seqNum", frameCtx.getSeqNum()).build();
    }

    @Override
    protected boolean isFinished(FileTransferStatus status) {
        // check transfer state
        FileTransferState fts = status.getState();
        if (fts != FileTransferState.ACTIVE) {
            throw new IllegalStateException("Invalid transfer state, name=" + fts);
        }
        // check frame seq number
        int lastSeqNum = status.getLastFrameSeqNum();
        int seqNum = frameCtx.getSeqNum();
        // test if succeeded
        if (seqNum == lastSeqNum) {
            return true;
        }
        // test if match with previous frame
        if (seqNum == lastSeqNum + 1) {
            return false;
        }
        throw new IllegalStateException("Cannot recover from last server frame, seqNum=" + lastSeqNum);
    }
}
