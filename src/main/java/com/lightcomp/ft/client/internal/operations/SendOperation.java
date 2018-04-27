package com.lightcomp.ft.client.internal.operations;

import com.lightcomp.ft.client.internal.UploadFrameContext;
import com.lightcomp.ft.core.TransferInfo;
import com.lightcomp.ft.exception.CanceledException;
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.wsdl.v1.FileTransferService;
import com.lightcomp.ft.xsd.v1.FileTransferState;
import com.lightcomp.ft.xsd.v1.FileTransferStatus;
import com.lightcomp.ft.xsd.v1.Frame;

public class SendOperation extends RecoverableOperation {

    private final UploadFrameContext frameCtx;

    public SendOperation(TransferInfo transferInfo, RecoveryHandler handler, UploadFrameContext frameCtx) {
        super(transferInfo, handler);
        this.frameCtx = frameCtx;
    }

    @Override
    protected void send(FileTransferService service) throws FileTransferException {
        Frame frame = frameCtx.createFrame();
        service.send(frame, transferInfo.getTransferId());
    }

    @Override
    protected TransferException createException(Throwable cause) throws CanceledException {
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
