package com.lightcomp.ft.server.internal;

import java.util.LinkedList;

import org.apache.commons.lang3.Validate;

import com.lightcomp.ft.core.recv.RecvFrameProcessor;

public class UploadFrameWorker implements Runnable {

    private enum State {
        RUNNING, STOPPING, TERMINATED, FINISHED
    }

    private final LinkedList<RecvFrameProcessor> frameQueue = new LinkedList<>();

    private final UploadTransfer transfer;

    private State state = State.RUNNING;

    public UploadFrameWorker(UploadTransfer transfer) {
        this.transfer = transfer;
    }

    public synchronized void terminate() {
        if (state == State.TERMINATED || state == State.FINISHED) {
            return;
        }
        state = State.STOPPING;
        // wait until terminate
        while (state != State.TERMINATED) {
            try {
                wait(100);
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    /**
     * Adds frame processor to worker queue.
     * 
     * @return Returns true when added. False is returned in case of finished worker.
     */
    public synchronized boolean addFrame(RecvFrameProcessor rfp) {
        Validate.notNull(rfp);

        if (state == State.RUNNING) {
            frameQueue.addLast(rfp);
            return true;
        }
        if (state == State.FINISHED) {
            return false;
        }
        throw new IllegalStateException("Worker is not running");
    }

    @Override
    public void run() {
        while (true) {
            RecvFrameProcessor rfp;
            synchronized (this) {
                if (state != State.RUNNING) {
                    break; // stopped worker
                }
                if (frameQueue.isEmpty()) {
                    state = State.FINISHED;
                    return;
                }
                rfp = frameQueue.removeFirst();
            }
            try {
                rfp.process();
                if (!transfer.addProcessedFrame(rfp.getSeqNum(), rfp.isLast())) {
                    break; // terminated transfer
                }
            } catch (Throwable t) {
                ErrorBuilder eb = new ErrorBuilder("Frame processor failed", transfer).addParam("seqNum", rfp.getSeqNum())
                        .setCause(t);
                transfer.transferFailed(eb);
                break; // processor failed
            }
        }
        // terminate transfer and clear queue
        synchronized (this) {
            state = State.TERMINATED;
            frameQueue.clear();
        }
    }
}
