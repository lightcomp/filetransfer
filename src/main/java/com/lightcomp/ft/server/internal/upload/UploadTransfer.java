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
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.xsd.v1.ErrorCode;
import com.lightcomp.ft.xsd.v1.Frame;

public class UploadTransfer extends AbstractTransfer {

    private final UploadWorker worker;

    private final Path uploadDir;

    private final FrameContext frameCtx;

    private Path tempUploadDir;

    private volatile int lastFrameSeqNum;

    private boolean processingFrame;

    public UploadTransfer(UploadAcceptor acceptor, String requestId, ServerConfig config) {
        super(acceptor, requestId, config);
        this.worker = new UploadWorker(this);
        this.uploadDir = acceptor.getUploadDir();
        this.frameCtx = new FrameContext(this, uploadDir);
    }

    @Override
    public int getLastFrameSeqNum() {
        return lastFrameSeqNum;
    }

    @Override
    public synchronized boolean isProcessingFrame() {
        return processingFrame;
    }

    @Override
    public synchronized void init() throws FileTransferException {
        super.init();
        try {
            // start worker
            Thread workerThread = new Thread(worker, "FileTransfer_UploadWorker");
            workerThread.start();
            // create temporary folder
            tempUploadDir = Files.createTempDirectory(uploadDir, "temp");
        } catch (Throwable t) {
            transferFailed(t);
            throw FileTransferExceptionBuilder.from(this, "Failed to initialize transfer").setCause(t).build();
        }
    }

    @Override
    public Frame sendFrame(long seqNum) throws FileTransferException {
        throw FileTransferExceptionBuilder.from(this, "Transfer cannot send data in upload mode").build();
    }

    public void onFrameFinished(FrameProcessor processor) throws CanceledException {
        TransferStatus ts;
        synchronized (this) {
            if (isCanceled()) {
                throw new CanceledException();
            }
            // update progress
            status.addTransferedFrame(processor.getSeqNum(), processor.getDataSize());
            // update current state if last frame
            if (processor.isLast()) {
                status.changeState(TransferState.TRANSFERED);
            }
            // copy status in synch block
            ts = status.copy();
        }
        acceptor.onTransferProgress(ts);
    }

    public void workerFailed(Throwable cause) {
        transferFailed(cause);
    }

    @Override
    public void receiveFrame(Frame frame) throws FileTransferException {
        synchronized (this) {
            TransferState ts = status.getState();
            if (ts != TransferState.STARTED) {
                throw FileTransferExceptionBuilder.from(this, "Transfer is unable to receive frame in current state")
                        .addParam("currentState", ts).setCause(status.getFailureCause()).build();
            }
            if (processingFrame) {
                throw FileTransferExceptionBuilder.from(this, "Transfer processing another frame").setCode(ErrorCode.BUSY)
                        .build();
            }
            processingFrame = true;
        }
        try {
            processFrame(frame);
        } catch (Throwable t) {
            transferFailed(t);
            throw FileTransferExceptionBuilder.from(t.getMessage()).setCause(t.getCause()).build();
        } finally {
            synchronized (this) {
                processingFrame = false;
            }
        }
    }

    private void processFrame(Frame frame) throws CanceledException {
        if (frame.getSeqNum() != lastFrameSeqNum + 1) {
            throw TransferExceptionBuilder.from(this, "Invalid frame sequential number")
                    .addParam("expectedSeqNum", lastFrameSeqNum + 1).addParam("receivedSeqNum", frame.getSeqNum()).build();
        }
        if (isCanceled()) {
            throw new CanceledException();
        }
        Path dataFile = prepareFrameData(frame);
        FrameProcessorImpl fp = FrameProcessorImpl.create(frame, frameCtx, dataFile);
        worker.addFrame(fp);
        lastFrameSeqNum++;
    }

    private Path prepareFrameData(Frame frame) throws CanceledException {
        String filePrefix = Integer.toString(frame.getSeqNum());
        try {
            Path file = Files.createTempFile(tempUploadDir, filePrefix, null);
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
}
