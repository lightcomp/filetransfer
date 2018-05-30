package com.lightcomp.ft.client.internal.operations;

import com.lightcomp.ft.client.internal.ExceptionType;
import com.lightcomp.ft.client.internal.operations.OperationResult.Type;
import com.lightcomp.ft.core.send.DataSendFailureCallback;
import com.lightcomp.ft.core.send.SendFrameContext;
import com.lightcomp.ft.wsdl.v1.FileTransferService;
import com.lightcomp.ft.xsd.v1.FileTransferState;
import com.lightcomp.ft.xsd.v1.Frame;
import com.lightcomp.ft.xsd.v1.SendRequest;
import com.lightcomp.ft.xsd.v1.TransferStatus;

public class SendOperation extends RecoverableOperation implements DataSendFailureCallback {

    private final SendFrameContext frameCtx;

    private Throwable dataSendFailureCause;

    private OperationResult result;

    public SendOperation(OperationHandler handler, FileTransferService servce, SendFrameContext frameCtx) {
        super(handler, servce);
        this.frameCtx = frameCtx;
    }

    @Override
    public void onDataSendFailed(Throwable cause) {
        dataSendFailureCause = cause;
    }

    @Override
    public OperationResult execute() {
        executeInternal();
        return result;
    }

    @Override
    protected void send() throws Throwable {
        Frame frame = frameCtx.prepareFrame(this);
        dataSendFailureCause = null;
        // send frame
        SendRequest sr = new SendRequest();
        sr.setFrame(frame);
        sr.setTransferId(getTransferId());
        service.send(sr);
        // check data send failure - MTOM does not fire exception
        if (dataSendFailureCause != null) {
            throw dataSendFailureCause;
        }
        result = new OperationResult(Type.SUCCESS);
    }

    @Override
    protected boolean prepareResend(TransferStatus status) {
        // check transfer state
        FileTransferState fts = status.getState();
        if (fts != FileTransferState.ACTIVE) {
            OperationError err = new OperationError("Failed to send frame, invalid server state").addParam("serverState", fts);
            result = new OperationResult(Type.FAIL, err);
            return false;
        }
        // check frame seq number
        int seqNum = frameCtx.getSeqNum();
        int serverSeqNum = status.getLastFrameSeqNum();
        // test if succeeded
        if (seqNum == serverSeqNum) {
            result = new OperationResult(Type.SUCCESS);
            return false;
        }
        // test if match with previous frame
        if (seqNum == serverSeqNum + 1) {
            return true;
        }
        // incorrect frame number
        OperationError err = new OperationError("Cannot recover last send frame").addParam("seqNum", seqNum)
                .addParam("serverSeqNum", serverSeqNum);
        result = new OperationResult(Type.FAIL, err);
        return false;
    }

    @Override
    protected void recoveryFailed(Type reason, Throwable src, ExceptionType srcType) {
        OperationError err = new OperationError("Failed to send frame").setCause(src).setCauseType(srcType).addParam("seqNum",
                frameCtx.getSeqNum());
        result = new OperationResult(reason, err);
    }
}
