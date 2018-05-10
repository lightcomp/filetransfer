package com.lightcomp.ft.server.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
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
import com.lightcomp.ft.exception.FileTransferExceptionBuilder;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.server.TransferState;
import com.lightcomp.ft.server.TransferStatus;
import com.lightcomp.ft.server.UploadAcceptor;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.xsd.v1.ErrorCode;
import com.lightcomp.ft.xsd.v1.Frame;

public class UploadTransfer extends AbstractTransfer implements RecvProgressInfo {

    private static final Logger logger = LoggerFactory.getLogger(UploadTransfer.class);

    private final TaskExecutor frameExecutor = new TaskExecutor(1);

    private final Path uploadDir;

    private final RecvContextImpl recvCtx;

    private Path tempDir;

    private int lastSeqNum;

    private boolean transferring;

    private boolean lastFrameReceived;

    public UploadTransfer(UploadAcceptor acceptor, int inactiveTimeout) {
        super(acceptor, inactiveTimeout);
        this.uploadDir = acceptor.getUploadDir();
        this.recvCtx = new RecvContextImpl(this, uploadDir);
    }

    @Override
    public synchronized boolean isBusy() {
        return super.isBusy() || transferring;
    }

    @Override
    public synchronized boolean isFinishing() {
        return super.isFinishing() || lastFrameReceived && status.getState() != TransferState.TRANSFERED;
    }

    public void init() {
        TransferStatus ts;
        synchronized (this) {
            // create temporary folder
            try {
                tempDir = Files.createTempDirectory(acceptor.getTransferId());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            // start frame executor
            frameExecutor.start();
            // update current state
            status.changeState(TransferState.STARTED);
            // copy status in synch block
            ts = status.copy();
        }
        acceptor.onTransferProgress(ts);
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
        acceptor.onTransferProgress(ts);
    }

    @Override
    public Frame sendFrame(long seqNum) throws FileTransferException {
        throw FileTransferExceptionBuilder.from("Transfer cannot send data in upload mode", acceptor).build();
    }

    @Override
    public void recvFrame(Frame frame) throws FileTransferException {
        synchronized (this) {
            // check if frames are being transferred
            if (transferring) {
                throw FileTransferExceptionBuilder.from("Transfer is busy", acceptor).setCode(ErrorCode.BUSY).build();
            }
            // check current transfer state
            TransferState ts = status.getState();
            if (ts != TransferState.STARTED) {
                throw FileTransferExceptionBuilder.from("Unable to receive frame", acceptor)
                        .addParam("currentState", ts.toExternal()).setCause(status.getFailureCause()).build();
            }
            // check if last frame received
            if (lastFrameReceived) {
                throw FileTransferExceptionBuilder.from("Server already received last frame", acceptor).build();
            }
            // check last frame number
            if (lastSeqNum + 1 != frame.getSeqNum()) {
                throw FileTransferExceptionBuilder.from("Invalid frame number", acceptor).addParam("expected", lastSeqNum + 1)
                        .addParam("given", frame.getSeqNum()).build();
            }
            lastFrameReceived = Boolean.TRUE.equals(frame.isLast());
            transferring = true;
            lastSeqNum++;
        }
        try {
            processFrame(frame);
        } catch (Throwable t) {
            // change internal state and notify acceptor
            transferFailed(t);
            // publish exception to client
            throw FileTransferExceptionBuilder.from("Failed to transfer frame", acceptor).addParam("seqNum", lastSeqNum)
                    .setCause(t).build();
        } finally {
            synchronized (this) {
                transferring = false;
            }
        }
    }

    /**
     * @return True when frame was added otherwise transfer is terminated and cannot
     *         accept more frames.
     */
    private boolean addProcessedFrame(int seqNum, boolean last) {
        TransferStatus ts;
        synchronized (this) {
            if (status.getState().isTerminal()) {
                return false; // terminated transfer
            }
            // check transfer state and last frame number
            Validate.isTrue(status.getState() == TransferState.STARTED);
            Validate.isTrue(status.getLastFrameSeqNum() + 1 == seqNum);
            // update progress
            status.incrementFrameSeqNum();
            // update current state if last frame
            if (last) {
                status.changeState(TransferState.TRANSFERED);
            }
            // copy status in synch block
            ts = status.copy();
        }
        acceptor.onTransferProgress(ts);
        return true;
    }

    private void transferFailed(Throwable cause) {
        TransferExceptionBuilder builder;
        boolean failed = false;

        synchronized (this) {
            TransferState ts = status.getState();
            if (ts == TransferState.CANCELED) {
                builder = TransferExceptionBuilder.from("Canceled transfer thrown exception");
            } else if (ts == TransferState.FINISHED) {
                builder = TransferExceptionBuilder.from("Finished transfer thrown exception");
            } else if (ts == TransferState.FAILED) {
                builder = TransferExceptionBuilder.from("Failed transfer thrown exception");
            } else {
                builder = TransferExceptionBuilder.from("Transfer failed");
                status.changeStateToFailed(cause);
                failed = true;
            }
        }
        builder.setTransfer(acceptor).setCause(cause).log(logger);
        if (failed) {
            acceptor.onTransferFailed(cause);
        }
    }

    private void processFrame(Frame frame) {
        RecvFrameProcessor rfp = RecvFrameProcessor.create(frame, recvCtx);
        rfp.transfer(tempDir);
        frameExecutor.addTask(() -> {
            try {
                rfp.process();
                // test if transfer is not terminated
                if (addProcessedFrame(rfp.getSeqNum(), rfp.isLast())) {
                    return;
                }
            } catch (Throwable t) {
                transferFailed(t);
            }
            // process failed or transfer terminated
            frameExecutor.stop();
        });
    }

    @Override
    protected void clearResources() {
        frameExecutor.stop();
        // delete temporary files
        if (tempDir != null) {
            try {
                PathUtils.deleteWithChildren(tempDir);
            } catch (IOException t) {
                TransferExceptionBuilder.from("Failed to delete temporary upload files", acceptor).setCause(t).log(logger);
            }
        }
    }
}
