package com.lightcomp.ft.server.internal.upload;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.lightcomp.ft.exception.CanceledException;
import com.lightcomp.ft.exception.FileTransferExceptionBuilder;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.server.ServerConfig;
import com.lightcomp.ft.server.TransferState;
import com.lightcomp.ft.server.TransferStatus;
import com.lightcomp.ft.server.UploadAcceptor;
import com.lightcomp.ft.server.internal.AbstractTransfer;
import com.lightcomp.ft.xsd.v1.ErrorCode;
import com.lightcomp.ft.xsd.v1.Frame;

import cxf.FileTransferException;

public class UploadTransfer extends AbstractTransfer {

    private final FrameWorker frameWorker;

    private final Path uploadDir;

    private Path tempUploadDir;

    private volatile int lastFrameSeqNum;

    private boolean processingFrame;

    public UploadTransfer(UploadAcceptor acceptor, String requestId, ServerConfig config) {
        super(acceptor, requestId, config);
        this.frameWorker = new FrameWorker(this);
        this.uploadDir = acceptor.getUploadDir();
    }

    @Override
    public int getLastFrameSeqNum() {
        return lastFrameSeqNum;
    }

    @Override
    public void begin() throws FileTransferException {
        Thread workerThread = new Thread(frameWorker, "FileTransfer_FrameWorker");
        workerThread.start();
        super.begin();
    }

    @Override
    public Frame sendFrame(long seqNum) throws FileTransferException {
        throw FileTransferExceptionBuilder.from(this, "Transfer cannot send data in upload mode").build();
    }

    @Override
    public void receiveFrame(Frame frame) throws FileTransferException {
        synchronized (this) {
            TransferState currState = status.getState();
            if (currState != TransferState.STARTED) {
                throw FileTransferExceptionBuilder.from(this, "Transfer is unable to receive frame in current state")
                        .addParam("currentState", currState).setCause(status.getFailureCause()).build();
            }
            if (processingFrame) {
                throw FileTransferExceptionBuilder.from(this, "Transfer processing another frame").setCode(ErrorCode.BUSY)
                        .build();
            }
            processingFrame = true;
        }
        try {
            processFrame(frame);
        } finally {
            synchronized (this) {
                processingFrame = false;
            }
        }
    }

    public void workerFinished(FrameContext frame) {
        TransferStatus ts;
        synchronized (this) {
            status.addTransferedFrame(frame.getSeqNum(), frame.getDataSize());
            // update current state if last frame
            if (frame.isLast()) {
                status.changeState(TransferState.TRANSFERED);
            }
            // copy status in synch block
            ts = status.copy();
            // notify canceling threads
            notifyAll();
        }
        acceptor.onTransferProgress(ts);
    }

    public void workerFailed(Throwable cause) {
        if (isCancelRequested() && cause instanceof CanceledException) {
            transferCanceled();
        } else {
            transferFailed(cause);
        }
    }

    private void processFrame(Frame frame) throws FileTransferException {
        if (frame.getSeqNum() != lastFrameSeqNum + 1) {
            throw FileTransferExceptionBuilder.from(this, "Invalid frame sequential number")
                    .addParam("expectedSeqNum", lastFrameSeqNum + 1).addParam("receivedSeqNum", frame.getSeqNum()).build();
        }
        try {
            Path dataFile = prepareFrameData(frame);
            frameWorker.addFrame(frame, dataFile);
            lastFrameSeqNum++;
        } catch (CanceledException ce) {
            transferCanceled();
            // publish exception to client
            throw FileTransferExceptionBuilder.from(this, "Transfer was canceled").build();
        } catch (Throwable t) {
            transferFailed(t);
            // publish exception to client
            throw FileTransferExceptionBuilder.from(t.getMessage()).setCause(t.getCause()).build();
        }
    }

    private Path prepareFrameData(Frame frame) throws CanceledException {
        CanceledException.checkTransfer(this);

        String filePrefix = Integer.toString(frame.getSeqNum());
        try {
            Path dir = getTempUploadDir();
            Path file = Files.createTempFile(dir, filePrefix, null);
            try (InputStream is = frame.getData().getInputStream()) {
                long length = Files.copy(is, file, StandardCopyOption.REPLACE_EXISTING);
                if (length != frame.getDataSize()) {
                    throw TransferExceptionBuilder.from(this, "Data length does not match with frame size")
                            .addParam("frameSeqNum", frame.getSeqNum()).addParam("dataLength", length).build();
                }
            }
            return file;
        } catch (IOException e) {
            throw TransferExceptionBuilder.from(this, "Failed to process frame data").addParam("frameSeqNum", frame.getSeqNum())
                    .setCause(e).build();
        }
    }

    private Path getTempUploadDir() throws IOException {
        if (tempUploadDir == null) {
            tempUploadDir = Files.createTempDirectory(uploadDir, "temp");
        }
        return tempUploadDir;
    }
}
