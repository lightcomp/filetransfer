package com.lightcomp.ft.server.internal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.common.PathUtils;
import com.lightcomp.ft.common.TaskExecutor;
import com.lightcomp.ft.core.recv.RecvContextImpl;
import com.lightcomp.ft.core.recv.RecvFrameProcessor;
import com.lightcomp.ft.core.recv.RecvProgressInfo;
import com.lightcomp.ft.exception.TransferExBuilder;
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.server.ServerConfig;
import com.lightcomp.ft.server.TransferState;
import com.lightcomp.ft.server.TransferStatus;
import com.lightcomp.ft.server.UploadHandler;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.xsd.v1.Frame;

public class UploadTransfer extends ServerTransfer implements RecvProgressInfo {

    public static final int FRAME_CAPACITY = 10;

    private static final Logger logger = LoggerFactory.getLogger(UploadTransfer.class);

    private final RecvContextImpl recvCtx;

    private Path tempDir;

    private UploadFrameWorker frameWorker;

    private boolean receivingFrame;

    private boolean lastFrameReceived;

    public UploadTransfer(String transferId, UploadHandler handler, ServerConfig config, TaskExecutor executor) {
        super(transferId, handler, config, executor);
        this.recvCtx = new RecvContextImpl(this, handler.getUploadDir(), config.getChecksumAlg());
    }

    @Override
    public synchronized void init() throws TransferException {
        // create temporary folder
        try {
            tempDir = Files.createTempDirectory(config.getWorkDir(), transferId);
        } catch (IOException e) {
            TransferExBuilder eb = new TransferExBuilder("Failed to create temporary upload directory",
                    this).addParam("parentPath", config.getWorkDir()).setCause(e);
            eb.log(logger);
            throw eb.build();
        }
        super.init();
    }

    @Override
    public void onFileDataReceived(long size) {
        TransferStatus ts;
        synchronized (this) {
            status.addTransferedData(size);
            // copy status in synch block
            ts = status.copy();
        }
        // exception is caught by worker
        onTransferProgress(ts);
    }

    @Override
    protected boolean isBusyInternal() {
        // check if already receiving frame
        if (receivingFrame) {
            return true;
        }
        // cannot processes more frames
        if (frameWorker != null && frameWorker.getFrameCount() >= FRAME_CAPACITY) {
            return true;
        }
        // check if processing last frame
        if (lastFrameReceived && status.getState() == TransferState.STARTED) {
            return true;
        }
        return false;
    }

    @Override
    public void recvFrame(Frame frame) throws FileTransferException {
        ServerError err = null;
        TransferStatus ts = null;
        synchronized (this) {
            checkActiveTransfer();
            err = prepareReceive(frame);
            if (err == null) {
                // copy status in synch block
                ts = status.copy();
            } else if (err.isFatal()) {
                status.changeStateToFailed(err.getDesc());
                // notify canceling threads
                notifyAll();
            }
        }
        // handle any error outside of sync block
        if (err != null) {
            if (err.isFatal()) {
                transferFailed(err);
            }
            throw err.createEx();
        }
        // receive is blocked by flag but not synchronized
        receiveInternal(frame, ts);
    }

    private void receiveInternal(Frame frame, TransferStatus ts) throws FileTransferException {
        RecvFrameProcessor rfp = RecvFrameProcessor.create(recvCtx, frame);
        try {
            onTransferProgress(ts);
            rfp.prepareData(tempDir);
        } catch (Throwable t) {
            ServerError err = new ServerError("Failed to receive frame", this).addParam("seqNum", frame.getSeqNum())
                    .setCause(t);
            transferFailed(err);
            throw err.createEx();
        } finally {
            synchronized (this) {
                receivingFrame = false;
            }
        }
        startFrameProcessing(rfp);
    }

    /**
     * Prepares frame receive, method is called in synchronized block.
     * 
     * @return Returns error context if transfer failed.
     */
    private ServerError prepareReceive(Frame frame) {
        // check started state
        if (status.getState() != TransferState.STARTED) {
            return new ServerError("Unable to receive frame in current state", this).addParam("currentState",
                    status.getState());
        }
        // check last frame received
        if (lastFrameReceived) {
            return new ServerError("Server already received last frame", this);
        }
        // check last frame number
        int nextSeqNum = status.getTransferedSeqNum() + 1;
        if (nextSeqNum != frame.getSeqNum()) {
            return new ServerError("Failed to receive frame, invalid frame number", this)
                    .addParam("expectedSeqNum", nextSeqNum).addParam("receivedSeqNum", frame.getSeqNum());
        }
        lastFrameReceived = Boolean.TRUE.equals(frame.isLast());
        status.incrementTransferedSeqNum();
        receivingFrame = true;
        return null;
    }

    private synchronized void startFrameProcessing(RecvFrameProcessor rfp) {
        if (status.getState().isTerminal()) {
            return; // transfer was terminated during data preparing
        }
        if (frameWorker == null || !frameWorker.addFrame(rfp)) {
            // start new worker with the processor
            frameWorker = new UploadFrameWorker(this);
            frameWorker.addFrame(rfp);
            executor.addTask(frameWorker);
        }
    }

    /**
     * Handles processed frame.
     * 
     * @return True when worker can process next frame. False when worker must terminate.
     */
    boolean frameProcessed(RecvFrameProcessor rfp) {
        TransferStatus ts;
        synchronized (this) {
            // terminate this worker if transfer is terminated
            if (status.getState().isTerminal()) {
                frameWorker = null;
                return false;
            }
            // integrity checks
            Validate.isTrue(status.getState() == TransferState.STARTED);
            Validate.isTrue(status.getProcessedSeqNum() + 1 == rfp.getSeqNum());
            // if last frame change state and reset worker
            if (rfp.isLast()) {
                status.changeState(TransferState.TRANSFERED);
                frameWorker = null;
            }
            // update progress
            status.incrementProcessedSeqNum();
            // copy status in synch block
            ts = status.copy();
        }
        // exception will be caught by worker
        onTransferProgress(ts);
        return !rfp.isLast();
    }

    void frameProcessingFailed(ServerError err) {
        transferFailed(err);
        // terminated transfer -> no need to sync
        frameWorker = null;
    }

    @Override
    protected void cleanResources() {
        // stop frame worker
        if (frameWorker != null) {
            frameWorker.terminate();
            frameWorker = null;
        }
        // delete temporary files
        if (tempDir != null) {
            try {
                PathUtils.deleteWithChildren(tempDir);
                tempDir = null;
            } catch (IOException e) {
                ServerError err = new ServerError("Failed to delete temporary upload files", this).setCause(e);
                err.log(logger);
            }
        }
    }
}
