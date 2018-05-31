package com.lightcomp.ft.server.internal;

import java.util.LinkedList;

import org.apache.commons.lang3.Validate;

import com.lightcomp.ft.common.TaskExecutor;
import com.lightcomp.ft.core.send.DataSendFailureCallback;
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

public class DwnldTransfer extends ServerTransfer implements SendProgressInfo, DataSendFailureCallback {

    public static final int FRAME_CAPACITY = 3;

    private final LinkedList<SendFrameContext> frameQueue = new LinkedList<>();

    // used in async workers do not use locally
    private final FrameBuilder frameBuilder;

    private DwnldFrameWorker frameWorker;

    private SendFrameContext currFrame;

    private int lastFrameSeqNum = -1;

    protected DwnldTransfer(String transferId, DownloadHandler handler, ServerConfig config, TaskExecutor executor) {
        super(transferId, handler, config, executor);
        frameBuilder = new FrameBuilder(this, config);
        frameBuilder.init(handler.getItemIterator());
    }

    @Override
    public synchronized void init() throws TransferException {
        prepareWorker();
        super.init();
    }

    @Override
    public void onDataSendFailed(Throwable t) {
        ServerError err = new ServerError("Failed to send frame data", this).setCause(t);
        transferFailed(err);
    }

    @Override
    public void onFileDataSend(long size) {
        TransferStatus ts;
        synchronized (this) {
            status.addTransferedData(size);
            // copy status in synch block
            ts = status.copy();
        }
        // exception is caught by onDataSendFailed(Throwable)
        onTransferProgress(ts);
    }

    @Override
    protected boolean isBusyInternal() {
        return currFrame == null;
    }

    @Override
    public GenericDataType finish() throws FileTransferException {
        preFinish();
        return super.finish();
    }

    private void preFinish() throws FileTransferException {
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
            status.changeState(TransferState.TRANSFERED);
            // copy status in synch block
            ts = status.copy();
        }
        try {
            onTransferProgress(ts);
        } catch (Throwable t) {
            ServerError err = new ServerError("Progress callback of data handler cause exception", this).setCause(t);
            transferFailed(err);
            throw err.createEx();
        }
    }

    @Override
    public Frame sendFrame(int seqNum) throws FileTransferException {
        DwnldFrameResult result;
        synchronized (this) {
            checkActiveTransfer();
            result = sendInternal(seqNum);
            // fail transfer if fatal error
            ServerError err = result.getError();
            if (err != null && err.isFatal()) {
                status.changeStateToFailed(err.getDesc());
                // notify canceling threads
                notifyAll();
            }
        }
        // handle any error outside of sync block
        ServerError err = result.getError();
        if (err != null) {
            if (err.isFatal()) {
                transferFailed(err);
            }
            throw err.createEx();
        }
        // report progress when status changed
        if (result.getStatus() != null) {
            try {
                onTransferProgress(result.getStatus());
            } catch (Throwable t) {
                err = new ServerError("Progress callback of data handler cause exception", this).setCause(t);
                transferFailed(err);
                throw err.createEx();
            }
        }
        return result.getFrameCtx().prepareFrame(this);
    }

    private DwnldFrameResult sendInternal(int seqNum) {
        // checks started state
        if (status.getState() != TransferState.STARTED) {
            ServerError err = new ServerError("Unable to send frame in current state", this).addParam("currentState",
                    status.getState());
            return new DwnldFrameResult(err);
        }
        int trSeqNum = status.getTransferedSeqNum();
        // return result if current frame
        if (trSeqNum == seqNum) {
            Validate.isTrue(currFrame.getSeqNum() == seqNum);
            return new DwnldFrameResult(currFrame);
        }
        // checks number of next frame
        if (trSeqNum != seqNum - 1) {
            ServerError err = new ServerError("Failed to send frame, invalid frame number", this)
                    .addParam("lastSeqNum", currFrame.getSeqNum()).addParam("receivedSeqNum", seqNum);
            return new DwnldFrameResult(err);
        }
        // return result if first request
        if (trSeqNum == 0) {
            Validate.isTrue(currFrame.getSeqNum() == 1);
            status.incrementTransferedSeqNum();
            return new DwnldFrameResult(currFrame, status.copy());
        }
        // check if current is not last
        if (currFrame.isLast()) {
            ServerError err = new ServerError("Requested frame exceeds last frame of transfer", this);
            return new DwnldFrameResult(err);
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
                ServerError err = new ServerError("Transfer is busy", this).setCode(ErrorCode.BUSY);
                return new DwnldFrameResult(err);
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
     * @return True when worker can process next frame. False when worker must terminate.
     */
    boolean frameProcessed(SendFrameContext frameCtx) {
        boolean processNext;
        TransferStatus ts;
        synchronized (this) {
            // terminate this worker if transfer is terminated
            if (status.getState().isTerminal()) {
                frameWorker = null;
                return false;
            }
            processNext = frameProcessedInternal(frameCtx);
            // update progress
            status.incrementProcessedSeqNum();
            // copy status in synch block
            ts = status.copy();
        }
        // exception is caught by worker
        onTransferProgress(ts);
        return processNext;
    }

    void frameProcessingFailed(ServerError err) {
        transferFailed(err);
        // terminated transfer -> no need to sync
        frameWorker = null;
    }

    /**
     * Handles processed frame. Caller must ensure synchronization.
     * 
     * @return True when worker can process next frame. False when worker must terminate.
     */
    private boolean frameProcessedInternal(SendFrameContext frameCtx) {
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
    protected void cleanResources() {
        // stop frame worker
        if (frameWorker != null) {
            frameWorker.terminate();
            frameWorker = null;
        }
    }
}