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
import com.lightcomp.ft.xsd.v1.Frame;
import com.lightcomp.ft.xsd.v1.GenericDataType;

public class DwnldTransfer extends AbstractTransfer implements SendProgressInfo {

    public static final int MAX_PREPARED_FRAMES = 3;

    private final LinkedList<SendFrameContext> preparedFrames = new LinkedList<>();

    // used in async workers do not use locally
    private final FrameBuilder frameBuilder;

    private DwnldFrameWorker frameWorker;

    private SendFrameContext currFrameCtx;

    protected DwnldTransfer(String transferId, DownloadHandler handler, ServerConfig config, TaskExecutor executor) {
        super(transferId, handler, config, executor);
        frameBuilder = new FrameBuilder(handler.getItemIterator(), this, config);
    }

    @Override
    public void init() throws TransferException {
        super.init();
        // start async worker
        frameWorker = new DwnldFrameWorker(this, frameBuilder);
        for (int i = 0; i < MAX_PREPARED_FRAMES; i++) {
            frameWorker.prepareFrame();
        }
        executor.addTask(frameWorker);
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

    public synchronized boolean isCurrentFrameLast() {
        return currFrameCtx != null && currFrameCtx.isLast();
    }

    @Override
    public GenericDataType finish() throws FileTransferException {
        TransferStatus ts = null;
        synchronized (this) {
            // switch to transfered if last frame sent and transfer is running
            if (isCurrentFrameLast() && status.getState() == TransferState.STARTED) {
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
        if (!isCurrentFrameLast()) {
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
        DwnldFrameProcessor frameProcessor = new DwnldFrameProcessor(seqNum, this, handler);
        synchronized (this) {
            checkActiveTransfer();
            frameProcessor.prepare(status, currFrameCtx);
        }
        return frameProcessor.process();
    }

    /**
     * @return Returns next frame or null when no frame is prepared.
     */
    synchronized SendFrameContext moveToNextFrame() {
        if (preparedFrames.isEmpty()) {
            return null;
        }
        currFrameCtx = preparedFrames.removeLast();
        if (!frameWorker.prepareFrame()) {
            frameWorker = new DwnldFrameWorker(this, frameBuilder);
            frameWorker.prepareFrame();
            executor.addTask(frameWorker);
        }
        return currFrameCtx;
    }

    /**
     * @return Returns true when frame was added. If false transfer is not able to accept new frames.
     */
    synchronized boolean addPreparedFrame(SendFrameContext frameCtx) {
        if (status.getState().isTerminal()) {
            return false; // terminated transfer
        }
        // integrity checks
        Validate.isTrue(status.getState() == TransferState.STARTED);
        Validate.isTrue(preparedFrames.size() <= MAX_PREPARED_FRAMES);
        if (preparedFrames.isEmpty()) {
            Validate.isTrue(status.getLastFrameSeqNum() + 1 == frameCtx.getSeqNum());
        } else {
            Validate.isTrue(preparedFrames.getFirst().getSeqNum() + 1 == frameCtx.getSeqNum());
        }
        // add prepared frame
        preparedFrames.addFirst(frameCtx);
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