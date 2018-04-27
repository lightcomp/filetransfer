package com.lightcomp.ft.server.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Iterator;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.core.recv.RecvContextImpl;
import com.lightcomp.ft.core.recv.RecvFrameProcessor;
import com.lightcomp.ft.core.recv.RecvProgressInfo;
import com.lightcomp.ft.exception.FileTransferExceptionBuilder;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.server.ServerConfig;
import com.lightcomp.ft.server.TransferState;
import com.lightcomp.ft.server.TransferStatus;
import com.lightcomp.ft.server.UploadAcceptor;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.xsd.v1.ErrorCode;
import com.lightcomp.ft.xsd.v1.Frame;

public class UploadTransfer extends AbstractTransfer implements RecvProgressInfo {

    private static final Logger logger = LoggerFactory.getLogger(UploadTransfer.class);

    private final RecvContextImpl recvCtx;

    private final UploadFrameWorker frameWorker;

    private final Path uploadDir;

    private Path tempUploadDir;

    private boolean processingFrame;

    public UploadTransfer(UploadAcceptor acceptor, String requestId, ServerConfig config) {
        super(acceptor, requestId, config);
        this.recvCtx = new RecvContextImpl(this);
        this.frameWorker = new UploadFrameWorker(this);
        this.uploadDir = acceptor.getUploadDir();
    }

    @Override
    public synchronized boolean isProcessingFrame() {
        return processingFrame;
    }

    @Override
    public void onDataReceived(long size) {
        TransferStatus ts;
        synchronized (this) {
            // update current state
            status.addTransferedData(size);
            
            ts = status.copy();
        }
        acceptor.onTransferProgress(ts);
    }

    @Override
    public void begin() throws FileTransferException {
        try {
            synchronized (this) {
                Validate.isTrue(status.getState() == TransferState.INITIALIZED);
                // start frame worker
                Thread frameWorkerThread = new Thread(frameWorker, "FileTransfer_UploadFrameWorker");
                frameWorkerThread.start();
                // create temporary folder
                tempUploadDir = Files.createTempDirectory(uploadDir, "temp");
                // update current state
                status.changeState(TransferState.STARTED);
            }
        } catch (Throwable t) {
            transferFailed(t);
            throw FileTransferExceptionBuilder.from(this, "Failed to initialize transfer").setCause(t).build();
        }
    }

    @Override
    public Frame sendFrame(long seqNum) throws FileTransferException {
        throw FileTransferExceptionBuilder.from(this, "Transfer cannot send data in upload mode").build();
    }

    @Override
    public void receiveFrame(Frame frame) throws FileTransferException {
        synchronized (this) {
            TransferState ts = status.getState();
            if (ts != TransferState.STARTED) {
                throw FileTransferExceptionBuilder.from(this, "Unable to receive frame").addParam("currentState", ts)
                        .setCause(status.getFailureCause()).build();
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

    private void processFrame(Frame frame) {
        int expectedSeqNum = getLastFrameSeqNum() + 1;
        if (frame.getSeqNum() != expectedSeqNum) {
            throw TransferExceptionBuilder.from(this, "Invalid frame sequential number")
                    .addParam("expectedSeqNum", expectedSeqNum).addParam("receivedSeqNum", frame.getSeqNum()).build();
        }
        Path dataFile = createDataFile(frame);
        RecvFrameProcessor rfp = RecvFrameProcessor.create(frame, recvCtx, dataFile);
        frameWorker.addProcessor(rfp);
    }

    private Path createDataFile(Frame frame) {
        String strSeqNum = Integer.toString(frame.getSeqNum());
        try {
            Path dataFile = Files.createTempFile(tempUploadDir, strSeqNum, null);
            try (InputStream is = frame.getData().getInputStream()) {
                long length = Files.copy(is, dataFile, StandardCopyOption.REPLACE_EXISTING);
                if (length != frame.getDataSize()) {
                    throw TransferExceptionBuilder.from(this, "Data length does not match with frame size")
                            .addParam("frameSeqNum", frame.getSeqNum()).addParam("dataLength", length).build();
                }
            }
            return dataFile;
        } catch (IOException e) {
            throw TransferExceptionBuilder.from(this, "Failed to process frame data").addParam("frameSeqNum", frame.getSeqNum())
                    .setCause(e).build();
        }
    }

    @Override
    protected void closeResources() {
        try {
            frameWorker.terminate();
            if (tempUploadDir != null) {
                Iterator<Path> fileIt = Files.walk(tempUploadDir).sorted(Comparator.reverseOrder()).iterator();
                while (fileIt.hasNext()) {
                    Files.delete(fileIt.next());
                }
            }
        } catch (Throwable t) {
            logger.error("Failed to close upload resources", t);
        }
    }
}
