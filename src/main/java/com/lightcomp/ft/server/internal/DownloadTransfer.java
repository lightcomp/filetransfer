package com.lightcomp.ft.server.internal;

import java.util.LinkedList;

import com.lightcomp.ft.common.TaskExecutor;
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

public class DownloadTransfer extends AbstractTransfer implements SendProgressInfo {

    public static final int MAX_PREPARED_FRAMES = 3;

    private final LinkedList<SendFrameContext> preparedFrames = new LinkedList<>();

    private final DwnldFrameWorker frameWorker;

    private SendFrameContext currFrameCtx;

    protected DownloadTransfer(String transferId, DownloadHandler handler, ServerConfig config, TaskExecutor executor) {
        super(transferId, handler, config, executor);
        frameWorker = new DwnldFrameWorker(this, handler.getItemIterator());
    }

    @Override
    public void init() throws TransferException {
        super.init();
        // start async builder
        executor.addTask(frameWorker);
        // TODO: fill current frame
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

    SendFrameContext moveToNextFrame() {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * @return Returns true when frame was added. If false transfer is not able to accept new frames.
     */
    public boolean addPreparedFrame(SendFrameContext frameCtx) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    protected void clearResources() {
        // stop frame worker
        frameWorker.terminate();
    }
}