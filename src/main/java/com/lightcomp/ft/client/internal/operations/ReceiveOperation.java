package com.lightcomp.ft.client.internal.operations;

import com.lightcomp.ft.client.internal.ExceptionType;
import com.lightcomp.ft.client.internal.operations.OperationResult.Type;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.wsdl.v1.FileTransferService;
import com.lightcomp.ft.xsd.v1.FileTransferState;
import com.lightcomp.ft.xsd.v1.Frame;
import com.lightcomp.ft.xsd.v1.ReceiveRequest;
import com.lightcomp.ft.xsd.v1.TransferStatus;

public class ReceiveOperation extends RecoverableOperation {

    private final int seqNum;

    private ReceiveResult result;

    public ReceiveOperation(OperationHandler handler, FileTransferService service, int seqNum) {
        super(handler, service);
        this.seqNum = seqNum;
    }

    @Override
    public ReceiveResult execute() {
        executeInternal();
        return result;
    }

    @Override
    protected void send() throws FileTransferException {
        ReceiveRequest rr = new ReceiveRequest();
        rr.setFrameSeqNum(seqNum);
        rr.setTransferId(getTransferId());
        Frame frame = service.receive(rr);
        prepareResult(frame);
    }

    private void prepareResult(Frame frame) {
        if (frame == null) {
            OperationError err = new OperationError("Frame was expected but null response received");
            result = new ReceiveResult(Type.FAIL, err);
        } else if (frame.getSeqNum() != seqNum) {
            OperationError err = new OperationError("Server returned invalid frame").addParam("requestedSeqNum", seqNum)
                    .addParam("receivedSeqNum", frame.getSeqNum());
            result = new ReceiveResult(Type.FAIL, err);
        } else {
            result = new ReceiveResult(Type.SUCCESS, frame);
        }
    }

    @Override
    protected boolean prepareResend(TransferStatus status) {
        // check transfer state
        FileTransferState fts = status.getState();
        if (fts != FileTransferState.ACTIVE) {
            OperationError err = new OperationError("Failed to receive frame, invalid server state").addParam("serverState", fts);
            result = new ReceiveResult(Type.FAIL, err);
            return false;
        }
        int serverSeqNum = status.getLastFrameSeqNum();
        // test if match with current frame
        if (seqNum == serverSeqNum) {
            return true;
        }
        // test if match with previous frame
        if (seqNum == serverSeqNum + 1) {
            return true;
        }
        OperationError err = new OperationError("Cannot recover last received frame").addParam("clientSeqNum", seqNum)
                .addParam("serverSeqNum", serverSeqNum);
        result = new ReceiveResult(Type.FAIL, err);
        return false;
    }

    @Override
    protected void recoveryFailed(Type reason, Throwable src, ExceptionType srcType) {
        OperationError err = new OperationError("Failed to receive frame").setCause(src).setCauseType(srcType).addParam("seqNum",
                seqNum);
        result = new ReceiveResult(reason, err);
    }
}