package com.lightcomp.ft.server.internal.upload;

import java.util.LinkedList;

import com.lightcomp.ft.exception.CanceledException;

public class UploadWorker implements Runnable {

    private final LinkedList<FrameProcessor> frameQueue = new LinkedList<>();

    private final UploadTransfer transfer;

    public UploadWorker(UploadTransfer transfer) {
        this.transfer = transfer;
    }

    public synchronized void addFrame(FrameProcessor frameProcessor) {
        frameQueue.addLast(frameProcessor);
        // notify worker thread
        notifyAll();
    }

    @Override
    public void run() {
        while (true) {
            try {
                FrameProcessor fp = getNextFrame();
                // stop if terminated
                if (transfer.isCanceled()) {
                    throw new CanceledException();
                }
                // process next frame
                fp.process();
                // notify upload
                transfer.onFrameFinished(fp);
                // stop if last
                if (fp.isLast()) {
                    return;
                }
            } catch (Throwable t) {
                transfer.workerFailed(t);
                return;
            }
        }
    }

    private synchronized FrameProcessor getNextFrame() throws CanceledException {
        while (frameQueue.isEmpty()) {
            if (transfer.isCanceled()) {
                throw new CanceledException();
            }
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        return frameQueue.getFirst();
    }
}