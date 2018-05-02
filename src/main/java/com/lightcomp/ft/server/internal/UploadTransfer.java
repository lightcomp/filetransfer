package com.lightcomp.ft.server.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Iterator;

import org.apache.commons.lang3.Validate;

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

    private final String requestId;

    private final RecvContextImpl recvCtx;

    private final UploadFrameWorker frameWorker;

    private final Path uploadDir;

    private Path tempUploadDir;

    private boolean processingFrame;

    public UploadTransfer(UploadAcceptor acceptor, String requestId, ServerConfig serverConfig) {
        super(acceptor, serverConfig);
        this.requestId = requestId;
        this.recvCtx = new RecvContextImpl(this);
        this.frameWorker = new UploadFrameWorker(this);
        this.uploadDir = acceptor.getUploadDir();
    }

    @Override
    public String getRequestId() {
        return requestId;
    }

    @Override
    protected boolean isProcessingFrame() {
        return processingFrame;
    }

    public synchronized void init() {
        Validate.isTrue(status.getState() == TransferState.INITIALIZED);
        // start frame worker
        Thread frameWorkerThread = new Thread(frameWorker, "FileTransfer_UploadFrameWorker");
        frameWorkerThread.start();
        // create temporary folder
        try {
            tempUploadDir = Files.createTempDirectory(uploadDir, "temp");
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create temporary upload folder", e);
        }
        // update current state
        status.changeState(TransferState.STARTED);
    }

    /**
     * @return True when frame was added otherwise transfer is terminated and cannot
     *         accept more frames.
     */
    public boolean addProcessedFrame(int seqNum, boolean last) {
        TransferStatus ts;
        synchronized (this) {
            if (status.getState().ordinal() >= TransferState.FINISHED.ordinal()) {
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

    public void frameProcessingFailed(Throwable cause) {
        TransferExceptionBuilder teb;
        boolean failed = false;

        synchronized (this) {
            TransferState ts = status.getState();
            if (ts == TransferState.CANCELED) {
                teb = TransferExceptionBuilder.from("Canceled transfer thrown exception");
            } else if (ts == TransferState.FINISHED) {
                teb = TransferExceptionBuilder.from("Finished transfer thrown exception");
            } else if (ts == TransferState.FAILED) {
                teb = TransferExceptionBuilder.from("Failed transfer thrown exception");
            } else {
                teb = TransferExceptionBuilder.from("Transfer failed");
                status.changeStateToFailed(cause);
                failed = true;
            }
        }
        teb.setTransfer(this).setCause(cause).log(logger);
        if (failed) {
            acceptor.onTransferFailed(cause);
        }
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
    public Frame sendFrame(long seqNum) throws FileTransferException {
        throw FileTransferExceptionBuilder.from(this, "Transfer cannot send data in upload mode").build();
    }

    @Override
    public void recvFrame(Frame frame) throws FileTransferException {
        synchronized (this) {
            // check current transfer state
            TransferState ts = status.getState();
            if (ts != TransferState.STARTED) {
                throw FileTransferExceptionBuilder.from(this, "Unable to receive frame")
                        .addParam("currentState", TransferState.convert(ts)).setCause(status.getFailureCause()).build();
            }
            // check if frame number is sequential
            int nextSeqNum = status.getLastFrameSeqNum() + 1;
            if (nextSeqNum != frame.getSeqNum()) {
                throw TransferExceptionBuilder.from("Invalid frame sequential number", this).addParam("nextSeqNum", nextSeqNum)
                        .addParam("receivedSeqNum", frame.getSeqNum()).build();
            }
            // we can process only one frame at same time
            if (processingFrame) {
                throw FileTransferExceptionBuilder.from(this, "Transfer processing another frame").setCode(ErrorCode.BUSY)
                        .build();
            }
            processingFrame = true;
        }
        try {
            processFrame(frame);
        } catch (Throwable t) {
            // we must change internal state and notify acceptor
            frameProcessingFailed(t);
            // publish exception to caller (client)
            throw FileTransferExceptionBuilder.from(t.getMessage()).setCause(t.getCause()).build();
        } finally {
            synchronized (this) {
                processingFrame = false;
            }
        }
    }

    @Override
    protected void clearResources() {
        frameWorker.terminate();
        if (tempUploadDir != null) {
            try {
                // create folder iterator with directory at last position
                Iterator<Path> itemIt = Files.walk(tempUploadDir).sorted(Comparator.reverseOrder()).iterator();
                // delete all content (temp directory included)
                while (itemIt.hasNext()) {
                    Files.delete(itemIt.next());
                }
            } catch (Throwable t) {
                logger.error("Failed to clear upload resources", t);
            }
        }
    }

    private void processFrame(Frame frame) {
        Path dataFile = createFrameDataFile(frame);
        RecvFrameProcessor rfp = RecvFrameProcessor.create(frame, recvCtx, dataFile);
        frameWorker.addProcessor(rfp);
    }

    private Path createFrameDataFile(Frame frame) {
        String seqNum = Integer.toString(frame.getSeqNum());
        try {
            Path dataFile = Files.createTempFile(tempUploadDir, seqNum, null);
            // copy whole data stream to temp file
            try (InputStream is = frame.getData().getInputStream()) {
                long length = Files.copy(is, dataFile, StandardCopyOption.REPLACE_EXISTING);
                // copied length must match with specified data size
                if (length != frame.getDataSize()) {
                    throw TransferExceptionBuilder.from("Data length does not match with frame size", this)
                            .addParam("frameSeqNum", frame.getSeqNum()).addParam("dataLength", length).build();
                }
            }
            return dataFile;
        } catch (IOException e) {
            throw TransferExceptionBuilder.from("Failed to process frame data", this).addParam("frameSeqNum", frame.getSeqNum())
                    .setCause(e).build();
        }
    }
}
