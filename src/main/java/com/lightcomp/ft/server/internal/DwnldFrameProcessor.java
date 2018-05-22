package com.lightcomp.ft.server.internal;

import org.apache.commons.lang3.Validate;

import com.lightcomp.ft.core.send.SendFrameContext;
import com.lightcomp.ft.server.TransferDataHandler;
import com.lightcomp.ft.server.TransferState;
import com.lightcomp.ft.server.TransferStatus;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.xsd.v1.ErrorCode;
import com.lightcomp.ft.xsd.v1.Frame;

public class DwnldFrameProcessor {

    private final int seqNum;

    private final DownloadTransfer transfer;

    private final TransferDataHandler handler;

    private ErrorContext errorCtx;

    private SendFrameContext frameCtx;

    private TransferStatus statusCpy;

    public DwnldFrameProcessor(int seqNum, DownloadTransfer transfer, TransferDataHandler handler) {
        this.seqNum = seqNum;
        this.transfer = transfer;
        this.handler = handler;
    }

    public void prepare(TransferStatusImpl status, SendFrameContext currFrameCtx) {
        errorCtx = prepareFrame(status, currFrameCtx);
        if (errorCtx != null && errorCtx.isFatal()) {
            // state must be changed in same sync block
            status.changeStateToFailed(errorCtx.getDesc());
            // notify canceling threads
            notifyAll();
        }
    }

    public Frame process() throws FileTransferException {
        if (errorCtx != null) {
            if (errorCtx.isFatal()) {
                transfer.onTransferFailed(errorCtx);
            }
            throw errorCtx.createEx();
        }
        if (statusCpy != null) {
            handler.onTransferProgress(statusCpy);
        }
        return frameCtx.createFrame();
    }

    private ErrorContext prepareFrame(TransferStatusImpl status, SendFrameContext currFrameCtx) {
        // checks started state
        TransferState ts = status.getState();
        if (ts != TransferState.STARTED) {
            return new ErrorContext("Unable to send frame in current state", transfer).addParam("currentState", ts);
        }
        int currSeqNum = status.getLastFrameSeqNum();
        if (currSeqNum == seqNum) {
            // prepare current frame
            if (currFrameCtx != null) {
                frameCtx = currFrameCtx;
                return null;
            }
            return prepareNextFrame(status);
        }
        // validate next frame number
        if (currSeqNum != seqNum - 1) {
            return new ErrorContext("Failed to send frame, invalid frame number", transfer)
                    .addParam("lastSeqNum", currSeqNum).addParam("receivedSeqNum", seqNum);
        }
        // check if does not skipping frame
        if (currFrameCtx == null) {
            return new ErrorContext("Request for next frame was received while preparing previous one", transfer);
        }
        // check if current is not last
        if (currFrameCtx.isLast()) {
            return new ErrorContext("Requested frame exceeds last frame of transfer", transfer);
        }
        // prepare next frame
        return prepareNextFrame(status);
    }

    private ErrorContext prepareNextFrame(TransferStatusImpl status) {
        frameCtx = transfer.moveToNextFrame();
        if (frameCtx == null) {
            return new ErrorContext("Frame is not prepared yet", transfer).addParam("seqNum", seqNum)
                    .setCode(ErrorCode.BUSY);
        }
        Validate.isTrue(frameCtx.getSeqNum() == seqNum);
        // update transfer status
        status.incrementFrameSeqNum();
        statusCpy = status.copy();
        return null;
    }
}
