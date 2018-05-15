package com.lightcomp.ft.client.operations;

import com.lightcomp.ft.client.internal.UploadFrameContext;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.wsdl.v1.FileTransferService;
import com.lightcomp.ft.xsd.v1.FileTransferState;
import com.lightcomp.ft.xsd.v1.FileTransferStatus;
import com.lightcomp.ft.xsd.v1.Frame;

public class SendOperation extends RecoverableOperation {

    private final UploadFrameContext frameCtx;

    public SendOperation(String transferId, OperationHandler handler, UploadFrameContext frameCtx) {
        super(transferId, handler);
        this.frameCtx = frameCtx;
    }

    @Override
    public TransferExceptionBuilder prepareException(Throwable t) {
        return TransferExceptionBuilder.from("Failed to send frame").addParam("seqNum", frameCtx.getSeqNum()).setCause(t);
    }

    @Override
    protected boolean isFinished(FileTransferStatus status) {
        // check transfer state
        FileTransferState fts = status.getState();
        if (fts != FileTransferState.ACTIVE) {
            throw TransferExceptionBuilder.from("Invalid transfer state").addParam("name", fts).build();
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
        throw TransferExceptionBuilder.from("Cannot recover last send frame").addParam("seqNum", seqNum)
                .addParam("serverSeqNum", lastSeqNum).build();
    }

    @Override
    protected void send(FileTransferService service) throws FileTransferException {
        Frame frame = frameCtx.createFrame();
        service.send(frame, transferId);
    }
}
