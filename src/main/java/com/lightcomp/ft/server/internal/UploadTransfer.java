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
import com.lightcomp.ft.exception.ErrorBuilder;
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.server.ServerConfig;
import com.lightcomp.ft.server.TransferState;
import com.lightcomp.ft.server.TransferStatus;
import com.lightcomp.ft.server.UploadHandler;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.xsd.v1.ErrorCode;
import com.lightcomp.ft.xsd.v1.Frame;

public class UploadTransfer extends AbstractTransfer implements RecvProgressInfo {

    private static final Logger logger = LoggerFactory.getLogger(UploadTransfer.class);

    private final RecvContextImpl recvCtx;

    private UploadFrameWorker frameWorker;

    private Path tempDir;

    private int lastSeqNum;

    private boolean transferring;

    private boolean lastFrameReceived;

    public UploadTransfer(String transferId, UploadHandler handler, ServerConfig config, TaskExecutor executor) {
        super(transferId, handler, config, executor);
        this.recvCtx = new RecvContextImpl(this, handler.getUploadDir());
    }

    @Override
    public synchronized boolean isBusy() {
        // busy transferring frame
        if (transferring) {
            return true;
        }
        // busy processing last frame
        if (lastFrameReceived && status.getState() != TransferState.TRANSFERED) {
            return true;
        }
        return super.isBusy();
    }

    @Override
    protected void checkPreparedFinish() throws FileTransferException {
        // check if busy processing last frame
        if (lastFrameReceived && status.getState() != TransferState.TRANSFERED) {
            throw new ErrorBuilder("Finish is not prepared").setTransfer(this).buildApiEx(ErrorCode.BUSY);
        }
    }

    @Override
    public void init() throws TransferException {
        // create temporary folder
        try {
            tempDir = Files.createTempDirectory(config.getWorkDir(), transferId);
        } catch (IOException e) {
            ErrorBuilder eb = new ErrorBuilder("Failed to create temporary upload directory").setTransfer(this)
                    .addParam("parentPath", config.getWorkDir()).setCause(e);
            // log complete IO exception, server will save only top level cause
            eb.log(logger);
            throw eb.buildEx();
        }
        // update state to started
        super.init();
    }

    @Override
    public void onDataReceived(long size) {
        TransferStatus ts;
        synchronized (this) {
            // update current state
            status.addTransferedData(size);
            // copy status in synch block
            ts = status.copy();
        }
        handler.onTransferProgress(ts);
    }

    @Override
    public Frame sendFrame(long seqNum) throws FileTransferException {
        ErrorBuilder eb = new ErrorBuilder("Transfer cannot send frame in upload mode").setTransfer(this);
        transferFailed(eb);
        throw eb.buildApiEx();
    }

    @Override
    public void recvFrame(Frame frame) throws FileTransferException {
        ErrorBuilder eb = null;
        synchronized (this) {
            checkActiveTransfer();
            // check transferring frame
            if (transferring) {
                throw new ErrorBuilder("Another frame is being transfered").setTransfer(this).buildApiEx(ErrorCode.BUSY);
            }
            // check started state
            if (status.getState() != TransferState.STARTED) {
                eb = new ErrorBuilder("Unable to receive frame in current state").setTransfer(this).addParam("currentState",
                        status.getState());
                // state must be changed in same sync block as check
                status.changeStateToFailed(eb.buildDesc());
                // notify canceling threads
                notifyAll();
            } else {
                transferring = true;
            }
        }
        // transfer failed must be called outside sync block
        if (eb != null) {
            transferFailed(eb);
            throw eb.buildApiEx();
        }
        // process frame
        try {
            processFrame(frame);
        } catch (Throwable t) {
            eb = new ErrorBuilder("Failed to process frame").setTransfer(this).addParam("seqNum", frame.getSeqNum());
            transferFailed(eb);
            eb.buildApiEx();
        } finally {
            synchronized (this) {
                transferring = false;
            }
        }
    }

    /**
     * Transfers frame data and starts async frame processor. Caller must ensure synchronization.
     */
    private void processFrame(Frame frame) throws TransferException {
        // check and set last frame received
        if (lastFrameReceived) {
            throw new TransferException("Server already received last frame");
        }
        lastFrameReceived = Boolean.TRUE.equals(frame.isLast());
        // check and set last seq number
        if (lastSeqNum + 1 != frame.getSeqNum()) {
            throw new ErrorBuilder("Invalid frame number").addParam("expectedSeqNum", lastSeqNum + 1).buildEx();
        }
        lastSeqNum++;

        // transfer data and start async processor
        RecvFrameProcessor rfp = RecvFrameProcessor.create(recvCtx, frame);
        rfp.prepareData(tempDir);

        // add frame to worker
        if (frameWorker != null && frameWorker.addFrame(rfp)) {
            return;
        }
        frameWorker = new UploadFrameWorker(this);
        frameWorker.addFrame(rfp);
        executor.addTask(frameWorker);
    }

    /**
     * @return True when frame was added otherwise transfer is terminated and cannot accept more frames.
     */
    boolean addProcessedFrame(int seqNum, boolean last) {
        TransferStatus ts;
        synchronized (this) {
            if (status.getState().isTerminal()) {
                return false; // terminated transfer
            }
            // integrity checks
            Validate.isTrue(status.getState() == TransferState.STARTED);
            Validate.isTrue(status.getLastFrameSeqNum() + 1 == seqNum);
            // update status
            status.incrementFrameSeqNum();
            if (last) {
                status.changeState(TransferState.TRANSFERED);
            }
            // copy status in synch block
            ts = status.copy();
        }
        handler.onTransferProgress(ts);
        return true;
    }

    @Override
    protected void clearResources() {
        // delete temporary files
        if (tempDir != null) {
            try {
                PathUtils.deleteWithChildren(tempDir);
            } catch (IOException e) {
                new ErrorBuilder("Failed to delete temporary upload files").setTransfer(this).setCause(e).log(logger);
            }
        }
        // stop frame worker
        if (frameWorker != null) {
            frameWorker.terminate();
        }
    }
}
