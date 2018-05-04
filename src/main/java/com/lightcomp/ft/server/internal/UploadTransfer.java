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

    private final TaskExecutor frameExecutor = new TaskExecutor(1);

    private final String requestId;

    private final Path rootDir;

    private final RecvContextImpl recvCtx;

    private Path tempDir;

    private int lastFrameSeqNum;

    private boolean lastFrameReceived;

    private boolean transferingFrame;

    public UploadTransfer(UploadAcceptor acceptor, int inactiveTimeout, String requestId) {
        super(acceptor, inactiveTimeout);
        this.requestId = requestId;
        this.rootDir = acceptor.getUploadDir();
        this.recvCtx = new RecvContextImpl(this, rootDir);
    }

    @Override
    public String getRequestId() {
        return requestId;
    }

    @Override
    public synchronized boolean isProcessingFrame() {
        return transferingFrame || !frameExecutor.isIddle();
    }

    public void init() {
        TransferStatus ts;
        synchronized (this) {
            Validate.isTrue(status.getState() == TransferState.INITIALIZED);
            // start frame executor
            frameExecutor.start();
            // create temporary folder
            try {
                tempDir = Files.createTempDirectory(rootDir, "temp");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
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
        throw FileTransferExceptionBuilder.from("Transfer cannot send data in upload mode", this).build();
    }

    @Override
    public void recvFrame(Frame frame) throws FileTransferException {
        synchronized (this) {
            // check current transfer state
            TransferState ts = status.getState();
            if (ts != TransferState.STARTED) {
                throw FileTransferExceptionBuilder.from("Unable to receive frame", this)
                        .addParam("currentState", TransferState.convert(ts)).setCause(status.getFailureCause()).build();
            }
            // check if last frame received
            if (lastFrameReceived) {
                throw FileTransferExceptionBuilder.from("Server already received last frame", this).build();
            }
            // we can process only one frame at same time
            if (transferingFrame) {
                throw FileTransferExceptionBuilder.from("Server already transferring a frame", this).setCode(ErrorCode.BUSY)
                        .build();
            }
            // check last sequence number
            if (lastFrameSeqNum + 1 != frame.getSeqNum()) {
                throw FileTransferExceptionBuilder.from("Invalid frame seqNum", this).addParam("expected", lastFrameSeqNum + 1)
                        .addParam("given", frame.getSeqNum()).build();
            }
            lastFrameReceived = Boolean.TRUE.equals(frame.isLast());
            transferingFrame = true;
            lastFrameSeqNum++;
        }
        try {
            transferFrame(frame);
        } catch (Throwable t) {
            // we must change internal state and notify acceptor
            frameProcessingFailed(t);
            // publish exception to caller (client)
            throw FileTransferExceptionBuilder.from(t.getMessage()).setCause(t.getCause()).build();
        } finally {
            synchronized (this) {
                transferingFrame = false;
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
            if (TransferState.isTerminal(status.getState())) {
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

    private void frameProcessingFailed(Throwable cause) {
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

    private void transferFrame(Frame frame) {
        Path frameData = prepareFrameData(frame);
        RecvFrameProcessor rfp = RecvFrameProcessor.create(frame, recvCtx, frameData);
        frameExecutor.addTask(() -> {
            try {
                rfp.process();
                // test if transfer is not terminated
                if (addProcessedFrame(rfp.getSeqNum(), rfp.isLast())) {
                    return;
                }
            } catch (Throwable t) {
                frameProcessingFailed(t);
            }
            // process failed or transfer terminated
            frameExecutor.stop();
        });
    }

    private Path prepareFrameData(Frame frame) {
        String seqNum = Integer.toString(frame.getSeqNum());
        try {
            Path dataFile = Files.createTempFile(tempDir, seqNum, null);
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
            throw TransferExceptionBuilder.from("Failed to transfer frame data", this).addParam("frameSeqNum", frame.getSeqNum())
                    .setCause(e).build();
        }
    }

    @Override
    protected void clearResources() {
        frameExecutor.stop();
        // delete temporary files
        if (tempDir != null) {
            try {
                // create iterator with directory at last position
                Iterator<Path> itemIt = Files.walk(tempDir).sorted(Comparator.reverseOrder()).iterator();
                // delete all (directory included)
                while (itemIt.hasNext()) {
                    Files.delete(itemIt.next());
                }
            } catch (Throwable t) {
                TransferExceptionBuilder.from("Failed to delete upload temporary files", this).setCause(t).log(logger);
            }
        }
    }
}
