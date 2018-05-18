package com.lightcomp.ft.server.internal;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.common.TaskExecutor;
import com.lightcomp.ft.core.send.FrameBlockBuilder;
import com.lightcomp.ft.core.send.SendFrameContext;
import com.lightcomp.ft.core.send.SendFrameContextImpl;
import com.lightcomp.ft.core.send.SendProgressInfo;
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.server.DownloadHandler;
import com.lightcomp.ft.server.ServerConfig;
import com.lightcomp.ft.server.TransferState;
import com.lightcomp.ft.server.TransferStatus;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.xsd.v1.ErrorCode;
import com.lightcomp.ft.xsd.v1.Frame;
import com.lightcomp.ft.xsd.v1.GenericDataType;

public class DownloadTransfer extends AbstractTransfer implements SendProgressInfo {

    private static final Logger logger = LoggerFactory.getLogger(DownloadTransfer.class);

    private final FrameBlockBuilder fbBuilder;

    private SendFrameContext currFrameCtx;

    private SendFrameContext frameCtxBuffer;

    private int currSeqNum;

    protected DownloadTransfer(String transferId, DownloadHandler handler, ServerConfig config, TaskExecutor executor) {
        super(transferId, handler, config, executor);
        fbBuilder = new FrameBlockBuilder(handler.getItemIterator(), this);
    }

    @Override
    public void init() throws TransferException {
        super.init();
        // prepareNextFrame(1);
        xxxx
    }

    @Override
    public synchronized boolean isBusy() {
        // check if preparing current frame
        if (currFrameCtx == null) {
            return true;
        }
        // call super
        return super.isBusy();
    }

    @Override
    public void onDataSend(long size) {
        TransferStatus ts;
        synchronized (this) {
            status.addTransferedData(size);
            // copy status in synch block
            ts = status.copy();
        }
        handler.onTransferProgress(ts);
    }

    @Override
    public GenericDataType finish() throws FileTransferException {
        TransferStatus ts = null;
        synchronized (this) {
            // switch to transfered if last frame sent and transfer is running
            if (currFrameCtx.isLast() && status.getState() == TransferState.STARTED) {
                status.changeState(TransferState.TRANSFERED);
                // copy status in synch block
                ts = status.copy();
            }
        }
        if (ts != null) {
            handler.onTransferProgress(ts);
        }
        // call super
        return super.finish();
    }

    @Override
    protected ErrorContext prepareFinish() {
        if (!currFrameCtx.isLast()) {
            return new ErrorContext("Failed to finish transfer, last frame wasn't downloaded", this)
                    .addParam("currentState", status.getState());
        }
        // call super
        return super.prepareFinish();
    }

    @Override
    public void recvFrame(Frame frame) throws FileTransferException {
        ErrorContext eb = new ErrorContext("Transfer cannot receive frame in download mode", this);
        transferFailed(eb);
        throw eb.createEx();
    }

    @Override
    public Frame sendFrame(int seqNum) throws FileTransferException {
        ErrorContext ec;
        synchronized (this) {
            checkActiveTransfer();
            ec = sendInternal(seqNum);
            // if succeeded return current frame
            if (ec == null) {
                return currFrameCtx.createFrame();
            } else if (ec.isFatal()) {
                // state must be changed in same sync block
                status.changeStateToFailed(ec.getDesc());
                // notify canceling threads
                notifyAll();
            }
        }
        // onTransferFailed must be called outside of sync block
        if (ec.isFatal()) {
            onTransferFailed(ec);
        }
        throw ec.createEx();
    }

    private ErrorContext sendInternal(int seqNum) {
        // check started state
        if (status.getState() != TransferState.STARTED) {
            return new ErrorContext("Unable to send frame in current state", this).addParam("currentState",
                    status.getState());
        }
        // check frame number
        if (currSeqNum != seqNum && currSeqNum != seqNum + 1) {
            return new ErrorContext("Failed to send frame, invalid frame number", this)
                    .addParam("currentSeqNum", currSeqNum).addParam("receivedSeqNum", seqNum);
        }
        if (currSeqNum == seqNum + 1) {
            // check if does not skipping frame
            if (currFrameCtx == null) {
                return new ErrorContext("Request for next frame was received while preparing previous one", this);
            }
            // check if current is not last
            if (currFrameCtx.isLast()) {
                return new ErrorContext("Frame number exceeds last frame", this).addParam("lastSeqNum", currSeqNum);
            }
            moveToNextFrame();
        }
        // check if frame prepared
        if (currFrameCtx == null) {
            return new ErrorContext("Frame is not prepared yet", this).addParam("seqNum", currSeqNum)
                    .setCode(ErrorCode.BUSY);
        }
        return null;
    }

    private void moveToNextFrame() {
        currFrameCtx = frameCtxBuffer;
        frameCtxBuffer = null;
        currSeqNum++;
        // TODO: start worker which will process frames until currFrameCtx and frameCtxBuffer is not null
    }

    private void prepareNextFrame(int seqNum) {
        SendFrameContext frameCtx = new SendFrameContextImpl(seqNum, maxFrameBlocks, maxFrameSize);
        fbBuilder.build(frameCtx);

        TransferStatus ts;
        synchronized (this) {
            if (status.getState().isTerminal()) {
                return;
            }
            // integrity checks
            Validate.isTrue(status.getState() == TransferState.STARTED);
            Validate.isTrue(status.getLastFrameSeqNum() + 1 == rfp.getSeqNum());

            nextFrameCtx = frameCtx;
            preparingNextFrame = false;
            // update status

            status.incrementFrameSeqNum();
            // copy status in synch block
            ts = status.copy();
        }
        handler.onTransferProgress(ts);
    }

    private void nextFramePreparationFailed(ErrorContext ec) {
        transferFailed(ec);
        /*
         * At this point transfer is already failed. Every check of preparingNextFrame is combined with
         * check of transfer state so flag reset is not critical in term of synchronization.
         */
        synchronized (this) {
            preparingNextFrame = false;
        }
    }

    @Override
    protected void clearResources() {
        // NOP
    }
}
