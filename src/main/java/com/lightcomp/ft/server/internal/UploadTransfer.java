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
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.server.ServerConfig;
import com.lightcomp.ft.server.TransferState;
import com.lightcomp.ft.server.TransferStatus;
import com.lightcomp.ft.server.UploadHandler;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.xsd.v1.Frame;

public class UploadTransfer extends AbstractTransfer implements RecvProgressInfo {

    private static final Logger logger = LoggerFactory.getLogger(UploadTransfer.class);

    private final RecvContextImpl recvCtx;

    private UploadFrameWorker frameWorker;

    private Path tempDir;

    private int lastSeqNum;

    private boolean receivingFrame;

    private boolean lastFrameReceived;

    public UploadTransfer(String transferId, UploadHandler handler, ServerConfig config, TaskExecutor executor) {
        super(transferId, handler, config, executor);
        this.recvCtx = new RecvContextImpl(this, handler.getUploadDir(), config.getChecksumAlg());
    }

    @Override
    public void init() throws TransferException {
        super.init();
        // create temporary folder
        try {
            tempDir = Files.createTempDirectory(config.getWorkDir(), transferId);
        } catch (IOException e) {
            TransferExceptionBuilder eb = new TransferExceptionBuilder("Failed to create temporary upload directory",
                    this).addParam("parentPath", config.getWorkDir()).setCause(e);
            eb.log(logger);
            throw eb.build();
        }
    }

    @Override
    public synchronized boolean isBusy() {
        // check if already receiving frame
        if (receivingFrame) {
            return true;
        }
        // check if processing last frame
        if (lastFrameReceived && status.getState() == TransferState.STARTED) {
            return true;
        }
        return false;
    }

    @Override
    public void onDataReceived(long size) {
        TransferStatus ts;
        synchronized (this) {
            status.addTransferedData(size);
            // copy status in synch block
            ts = status.copy();
        }
        handler.onTransferProgress(ts);
    }

    @Override
    public Frame sendFrame(int seqNum) throws FileTransferException {
        ErrorContext ec = new ErrorContext("Transfer cannot send frame in upload mode", this);
        transferFailed(ec);
        throw ec.createEx();
    }

    @Override
    public void recvFrame(Frame frame) throws FileTransferException {
        ErrorContext ec = null;
        synchronized (this) {
            checkActiveTransfer();
            ec = prepareReceive(frame);
            // fail transfer if fatal error
            if (ec != null && ec.isFatal()) {
                // state must be changed in same sync block
                status.changeStateToFailed(ec.getDesc());
                // notify canceling threads
                notifyAll();
            }
        }
        // onTransferFailed must be called outside of sync block
        if (ec != null) {
            if (ec.isFatal()) {
                onTransferFailed(ec);
            }
            throw ec.createEx();
        }
        // receive is blocked by flag but not synchronized
        try {
            receiveInternal(frame);
        } finally {
            synchronized (this) {
                receivingFrame = false;
            }
        }
    }

    /**
     * Prepares frame receive, method is called in synchronized block.
     * 
     * @return Returns error context if transfer failed.
     */
    private ErrorContext prepareReceive(Frame frame) {
        // check started state
        if (status.getState() != TransferState.STARTED) {
            return new ErrorContext("Unable to receive frame in current state", this).addParam("currentState",
                    status.getState());
        }
        // check last frame received
        if (lastFrameReceived) {
            return new ErrorContext("Server already received last frame", this);
        }
        // check last frame number
        int seqNum = frame.getSeqNum();
        if (seqNum != lastSeqNum + 1) {
            return new ErrorContext("Failed to receive frame, invalid frame number", this)
                    .addParam("expectedSeqNum", lastSeqNum + 1).addParam("receivedSeqNum", seqNum);
        }
        lastFrameReceived = Boolean.TRUE.equals(frame.isLast());
        receivingFrame = true;
        lastSeqNum = seqNum;
        return null;
    }

    /**
     * Transfers frame data and starts async frame processing.
     */
    private void receiveInternal(Frame frame) throws FileTransferException {
        RecvFrameProcessor rfp = RecvFrameProcessor.create(recvCtx, frame);
        // transfer data
        try {
            rfp.prepareData(tempDir);
        } catch (TransferException e) {
            ErrorContext ec = new ErrorContext("Failed to receive frame", this).addParam("seqNum", frame.getSeqNum())
                    .setCause(e);
            transferFailed(ec);
            throw ec.createEx();
        }
        // start async processor
        if (frameWorker != null && frameWorker.addFrame(rfp)) {
            return;
        }
        frameWorker = new UploadFrameWorker(this);
        frameWorker.addFrame(rfp);
        executor.addTask(frameWorker);
    }

    /**
     * @return Returns false when worker is no longer needed.
     */
    boolean frameProcessed(int seqNum, boolean last) {
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
                new ErrorContext("Failed to delete temporary upload files", this).setCause(e).log(logger);
            }
        }
    }
}
