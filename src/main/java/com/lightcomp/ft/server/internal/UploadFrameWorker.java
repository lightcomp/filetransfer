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

    /**
     * Adds frame processor to worker queue.
     * 
     * @return Returns false when worker is finished.
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
        throw new IllegalStateException("Worker is terminated");
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

    @Override
    public void run() {
        while (true) {
            RecvFrameProcessor rfp;
            synchronized (this) {
                if (state != State.RUNNING) {
                    state = State.TERMINATED;
                    return; // worker is stopping
                }
                if (frameQueue.isEmpty()) {
                    state = State.FINISHED;
                    return; // no more frame
                }
                rfp = frameQueue.removeFirst();
            }
            try {
                rfp.process();
                if (!transfer.frameProcessed(rfp.getSeqNum(), rfp.isLast())) {
                    break; // worker terminated
                }
            } catch (Throwable t) {
                ErrorContext ec = new ErrorContext("Frame processor failed", transfer)
                        .addParam("seqNum", rfp.getSeqNum()).setCause(t);
                transfer.transferFailed(ec);
                break; // processor failed
            }
        }
        synchronized (this) {
            state = State.TERMINATED;
            frameQueue.clear();
        }
    }
}
