package com.lightcomp.ft.client.operations;

import com.lightcomp.ft.client.internal.ExceptionType;
import com.lightcomp.ft.client.operations.OperationStatus.Type;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.wsdl.v1.FileTransferService;
import com.lightcomp.ft.xsd.v1.FileTransferState;
import com.lightcomp.ft.xsd.v1.Frame;
import com.lightcomp.ft.xsd.v1.ReceiveRequest;
import com.lightcomp.ft.xsd.v1.TransferStatus;

public class RecvOperation extends AbstractOperation {

    private final int seqNum;

    private Frame frame;

    public RecvOperation(OperationHandler handler, FileTransferService service, int seqNum) {
        super(handler, service);
        this.seqNum = seqNum;
    }

    public Frame getFrame() {
        return frame;
    }

    @Override
    protected void send() throws FileTransferException {
        ReceiveRequest rr = new ReceiveRequest();
        rr.setFrameSeqNum(seqNum);
        rr.setTransferId(handler.getTransferId());
        frame = service.receive(rr);
    }

    @Override
    protected OperationStatus resolveServerStatus(TransferStatus status) {
        // check transfer state
        FileTransferState fts = status.getState();
        if (fts != FileTransferState.ACTIVE) {
            return new OperationStatus(Type.FAIL).setFailureMessage("Failed to receive frame, invalid server state")
                    .addFailureParam("serverState", fts);
        }
        // check frame seq number
        int lastSeqNum = status.getLastFrameSeqNum();
        // test if succeeded
        if (seqNum == lastSeqNum) {
            return new OperationStatus(Type.SUCCESS);
        }
        // test if match with previous frame
        if (seqNum == lastSeqNum + 1) {
            return null; // next try
        }
        return new OperationStatus(Type.FAIL).setFailureMessage("Cannot recover last received frame")
                .addFailureParam("seqNum", seqNum).addFailureParam("serverSeqNum", lastSeqNum);
    }

    @Override
    protected OperationStatus operationFinished() {
        if (frame == null) {
            return new OperationStatus(Type.FAIL).setFailureMessage("Server returned invalid frame");
        }
        if (frame.getSeqNum() != seqNum) {
            return new OperationStatus(Type.FAIL).setFailureMessage("Server returned invalid frame")
                    .addFailureParam("requestedSeqNum", seqNum).addFailureParam("recievedSeqNum", frame.getSeqNum());
        }
        return super.operationFinished();
    }

    @Override
    protected OperationStatus recoveryFailed(Type type, Throwable ex, ExceptionType exType) {
        return super.recoveryFailed(type, ex, exType).setFailureMessage("Failed to receive frame")
                .addFailureParam("seqNum", seqNum);
    }
}