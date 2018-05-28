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
        prepareWorker();
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
            // check if last frame transfered
            if (status.getTransferedSeqNum() != lastFrameSeqNum) {
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
        DwnldFrameResult result;
        synchronized (this) {
            checkActiveTransfer();
            result = sendInternal(seqNum);
            // fail transfer if fatal error
            ErrorContext ec = result.getError();
            if (ec != null && ec.isFatal()) {
                // state must be changed in same sync block
                status.changeStateToFailed(ec.getDesc());
                // notify canceling threads
                notifyAll();
            }
        }
        // handle any error outside of sync block
        ErrorContext ec = result.getError();
        if (ec != null) {
            if (ec.isFatal()) {
                onTransferFailed(ec);
            }
            throw ec.createEx();
        }
        // report progress when frame changed
        if (result.isStatusChanged()) {
            handler.onTransferProgress(result.getStatus());
        }
        return result.getFrame();
    }

    /**
     * Caller must ensure synchronization.
     */
    private DwnldFrameResult sendInternal(int seqNum) {
        // checks started state
        if (status.getState() != TransferState.STARTED) {
            ErrorContext ec = new ErrorContext("Unable to send frame in current state", this).addParam("currentState",
                    status.getState());
            return new DwnldFrameResult(ec);
        }
        int trSeqNum = status.getTransferedSeqNum();
        // return if current frame
        if (trSeqNum == seqNum) {
            Validate.isTrue(currFrame.getSeqNum() == seqNum);
            return new DwnldFrameResult(currFrame);
        }
        // checks number of next frame
        if (trSeqNum != seqNum - 1) {
            ErrorContext ec = new ErrorContext("Failed to send frame, invalid frame number", this)
                    .addParam("lastSeqNum", currFrame.getSeqNum()).addParam("receivedSeqNum", seqNum);
            return new DwnldFrameResult(ec);
        }
        // return if first request
        if (trSeqNum == 0) {
            Validate.isTrue(currFrame.getSeqNum() == 1);
            status.incrementTransferedSeqNum();
            return new DwnldFrameResult(currFrame, status.copy());
        }
        // check if current is not last
        if (currFrame.isLast()) {
            ErrorContext ec = new ErrorContext("Requested frame exceeds last frame of transfer", this);
            return new DwnldFrameResult(ec);
        }
        // prepare next frame
        return moveToNextFrame(seqNum);
    }

    /**
     * Changes current frame, can be null when new frame is not prepared yet. Caller must ensure
     * synchronization.
     */
    private DwnldFrameResult moveToNextFrame(int seqNum) {
        // next can be already set to current by worker
        if (currFrame.getSeqNum() != seqNum) {
            // reset old current
            currFrame = null;
            // if queue is empty wait for worker
            if (frameQueue.isEmpty()) {
                ErrorContext ec = new ErrorContext("Transfer is busy", this).setCode(ErrorCode.BUSY);
                return new DwnldFrameResult(ec);
            }
            // set current from queue
            currFrame = frameQueue.removeFirst();
            prepareWorker();
        }
        // update last frame
        status.incrementTransferedSeqNum();
        // return current frame with copy of changed status
        return new DwnldFrameResult(currFrame, status.copy());
    }

    /**
     * Checks current worker and prepares new one if needed. Caller must ensure synchronization.
     */
    private void prepareWorker() {
        Validate.isTrue(!status.getState().isTerminal());
        // create new worker when needed
        if (frameWorker == null && lastFrameSeqNum < 0) {
            frameWorker = new DwnldFrameWorker(this, frameBuilder);
            executor.addTask(frameWorker);
        }
    }

    /**
     * Handles processed frame.
     * 
     * @return False when worker must terminate. True when worker can continue.
     */
    boolean frameProcessed(SendFrameContext frameCtx) {
        boolean canWorkerContinue;
        TransferStatus ts;
        synchronized (this) {
            // terminate this worker if transfer is terminated
            if (status.getState().isTerminal()) {
                return false;
            }
            canWorkerContinue = frameProcessedInternal(frameCtx);
            // update progress
            status.incrementProcessedSeqNum();
            // copy status in synch block
            ts = status.copy();
        }
        handler.onTransferProgress(ts);
        return canWorkerContinue;
    }

    void frameProcessingFailed(ErrorContext errorCtx) {
        transferFailed(errorCtx);
    }

    /**
     * Handles processed frame. Caller must ensure synchronization.
     * 
     * @return False when worker must terminate. True when worker can continue.
     */
    private boolean frameProcessedInternal(SendFrameContext frameCtx) {
        Validate.isTrue(status.getState() == TransferState.STARTED);
        // add processed frame
        if (currFrame == null) {
            currFrame = frameCtx;
        } else {
            Validate.isTrue(frameQueue.size() < FRAME_CAPACITY);
            frameQueue.addLast(frameCtx);
        }
        // terminate worker when frame is last
        if (frameCtx.isLast()) {
            lastFrameSeqNum = frameCtx.getSeqNum();
            frameWorker = null;
            return false;
        }
        // terminate worker when queue is full
        if (frameQueue.size() >= FRAME_CAPACITY) {
            frameWorker = null;
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