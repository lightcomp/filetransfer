package com.lightcomp.ft.server.internal.upload;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.LinkedList;

import com.lightcomp.ft.exception.CanceledException;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.xsd.v1.Frame;
import com.lightcomp.ft.xsd.v1.FrameBlock;

public class FrameWorker implements Runnable {

    private final LinkedList<FrameContextImpl> frameQueue = new LinkedList<>();

    private final UploadTransfer transfer;

    public FrameWorker(UploadTransfer transfer) {
        this.transfer = transfer;
    }

    public synchronized void addFrame(Frame frame, Path dataFile) {
        FrameContextImpl fc = FrameContextImpl.create(frame, dataFile);
        frameQueue.addLast(fc);
        // notify worker thread
        notifyAll();
    }

    @Override
    public void run() {
        FrameContextImpl lastFrameCtx = null;
        while (true) {
            try {
                CanceledException.checkTransfer(transfer);
                // get next frame
                FrameContextImpl frameCtx = getNextFrame();
                frameCtx.init(lastFrameCtx);
                lastFrameCtx = frameCtx;
                // save all blocks
                saveBlocks(frameCtx);
                // notify upload
                transfer.workerFinished(frameCtx);
                // exit if last
                if (frameCtx.isLast()) {
                    return;
                }
            } catch (Throwable t) {
                transfer.workerFailed(t);
                return;
            }
        }
    }

    private synchronized FrameContextImpl getNextFrame() throws CanceledException {
        while (frameQueue.isEmpty()) {
            CanceledException.checkTransfer(transfer);
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        return frameQueue.getFirst();
    }

    private void saveBlocks(FrameContext frameCtx) throws CanceledException {
        int blockNum = 1;
        try (InputStream is = frameCtx.openDataInputStream()) {
            for (FrameBlock block : frameCtx.getBlocks()) {
                CanceledException.checkTransfer(transfer);
                try {
                    block.saveBlock(is, frameCtx);
                } catch (Throwable t) {
                    throw TransferExceptionBuilder.from("Failed to save frame block")
                            .addParam("frameSeqNum", frameCtx.getSeqNum()).addParam("blockNum", blockNum).setCause(t).build();
                }
                blockNum++;
            }
        } catch (IOException e) {
            throw TransferExceptionBuilder.from("Failed to open frame data file").addParam("frameSeqNum", frameCtx.getSeqNum())
                    .setCause(e).build();
        }
    }
}