package com.lightcomp.ft.server.internal;

import java.util.LinkedList;

import org.apache.commons.lang3.Validate;

import com.lightcomp.ft.common.TaskExecutor;
import com.lightcomp.ft.core.send.FrameBuilder;
import com.lightcomp.ft.core.send.SendFrameContext;
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

public class DwnldTransfer extends AbstractTransfer implements SendProgressInfo {

    public static final int FRAME_CAPACITY = 3;

    private final LinkedList<SendFrameContext> frameQueue = new LinkedList<>();

    // used in async workers do not use locally
    private final FrameBuilder frameBuilder;

    private DwnldFrameWorker frameWorker;

    private SendFrameContext currFrame;

    private int lastFrameSeqNum = -1;

    protected DwnldTransfer(String transferId, DownloadHandler handler, ServerConfig config, TaskExecutor executor) {
        super(transferId, handler, config, executor);
        frameBuilder = new FrameBuilder(handler.getItemIterator(), this, config);
    }

    @Override
    public void init() throws TransferException {
        super.init();
        // start async worker
        frameWorker = new DwnldFrameWorker(this, frameBuilder, FRAME_CAPACITY);
        executor.addTask(frameWorker);
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
    public synchronized boolean isBusy() {
        // check if preparing current frame
        if (currFrame == null) {
            return true;
        }
        return false;
    }

    @Override
    public GenericDataType finish() throws FileTransferException {
        preFinish();
        return super.finish();
    }

    private void preFinish() {
        TransferStatus ts;
        synchronized (this) {
            // check if last frame sent
            if (status.getLastFrameSeqNum() != lastFrameSeqNum) {
                return;
            }
            // check started state
            if (status.getState() != TransferState.STARTED) {
                return;
            }
            // change state to transfered
            status.changeState(TransferState.TRANSFERED);
            // copy status in synch block
            ts = status.copy();
        }
        handler.onTransferProgress(ts);
    }

    @Override
    public void recvFrame(Frame frame) throws FileTransferException {
        ErrorContext eb = new ErrorContext("Transfer cannot receive frame in download mode", this);
        transferFailed(eb);
        throw eb.createEx();
    }

    @Override
    public Frame sendFrame(int seqNum) throws FileTransferException {
        DwnldFrameResponse resp;
        synchronized (this) {
            checkActiveTransfer();
            resp = prepareFrame(seqNum);
            // fail transfer if fatal error
            ErrorContext ec = resp.getError();
            if (ec != null && ec.isFatal()) {
                // state must be changed in same sync block
                status.changeStateToFailed(ec.getDesc());
                // notify canceling threads
                notifyAll();
            }
        }
        // handle any error outside of sync block
        ErrorContext ec = resp.getError();
        if (ec != null) {
            if (ec.isFatal()) {
                onTransferFailed(ec);
            }
            throw ec.createEx();
        }
        // report progress when frame changed
        if (resp.isFrameChanged()) {
            handler.onTransferProgress(resp.getStatus());
        }
        return resp.getFrame();
    }

    /**
     * Caller must ensure synchronization.
     */
    private DwnldFrameResponse prepareFrame(int seqNum) {
        // checks started state
        if (status.getState() != TransferState.STARTED) {
            ErrorContext ec = new ErrorContext("Unable to send frame in current state", this).addParam("currentState",
                    status.getState());
            return new DwnldFrameResponse(ec);
        }
        int sentSeqNum = status.getLastFrameSeqNum();
        // return if current requested
        if (sentSeqNum == seqNum) {
            Validate.isTrue(currFrame.getSeqNum() == seqNum);
            return new DwnldFrameResponse(currFrame);
        }
        // check number of next frame
        if (sentSeqNum != seqNum - 1) {
            ErrorContext ec = new ErrorContext("Failed to send frame, invalid frame number", this)
                    .addParam("lastSeqNum", currFrame.getSeqNum()).addParam("receivedSeqNum", seqNum);
            return new DwnldFrameResponse(ec);
        }
        // return if requested first time
        if (sentSeqNum == 0) {
            Validate.isTrue(currFrame.getSeqNum() == 1);
            // update transfer status
            status.incrementFrameSeqNum();
            // return current with copy of changed status
            return new DwnldFrameResponse(currFrame, status.copy());
        }
        // check if current is not last
        if (currFrame.isLast()) {
            ErrorContext ec = new ErrorContext("Requested frame exceeds last frame of transfer", this);
            return new DwnldFrameResponse(ec);
        }
        // prepare next frame
        return moveToNextFrame(seqNum);
    }

    /**
     * Caller must ensure synchronization.
     */
    private DwnldFrameResponse moveToNextFrame(int seqNum) {
        // next can be already set as current by worker
        if (currFrame.getSeqNum() != seqNum) {
            // replace old current with next from queue
            currFrame = null;
            if (frameQueue.isEmpty()) {
                ErrorContext ec = new ErrorContext("Transfer is busy", this).setCode(ErrorCode.BUSY);
                return new DwnldFrameResponse(ec);
            }
            currFrame = frameQueue.removeFirst();
            // notifies worker about frame removal
            prepareNextFrame();
        }
        // update transfer status
        status.incrementFrameSeqNum();
        // return current frame with copy of changed status
        return new DwnldFrameResponse(currFrame, status.copy());
    }

    private void prepareNextFrame() {
        boolean terminatedWoker = !frameWorker.prepareFrame();
        if (lastFrameSeqNum > 0) {
            // worker already prepared last frame a should be terminated
            Validate.isTrue(terminatedWoker);
            return;
        }
        if (terminatedWoker) {
            int frameCount = FRAME_CAPACITY - frameQueue.size();
            frameWorker = new DwnldFrameWorker(this, frameBuilder, frameCount);
            executor.addTask(frameWorker);
        }
    }

    /**
     * @return Returns false when worker is no longer needed.
     */
    synchronized boolean framePrepared(SendFrameContext frameCtx) {
        if (status.getState().isTerminal()) {
            return false; // terminated transfer
        }
        // integrity checks
        Validate.isTrue(status.getState() == TransferState.STARTED);
        Validate.isTrue(frameQueue.size() <= FRAME_CAPACITY);
        // add prepared frame
        if (currFrame == null) {
            currFrame = frameCtx;
        } else {
            frameQueue.addLast(frameCtx);
        }
        // worker not needed when frame is last
        if (frameCtx.isLast()) {
            lastFrameSeqNum = frameCtx.getSeqNum();
            return false;
        }
        return true;
    }

    @Override
    protected void clearResources() {
        // stop frame worker
        if (frameWorker != null) {
            frameWorker.terminate();
            frameWorker = null;
        }
    }
}