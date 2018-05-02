package com.lightcomp.ft.server.internal;

import java.util.LinkedList;

import org.apache.commons.lang3.Validate;

import com.lightcomp.ft.core.recv.RecvFrameProcessor;

public class UploadFrameWorker implements Runnable {

    /**
     * Internal worker state.
     */
    private enum State {
        RUNNING, STOPPING, TERMINATED
    }

    private final LinkedList<RecvFrameProcessor> queue = new LinkedList<>();

    private final UploadTransfer transfer;

    private State state = State.RUNNING;

    public UploadFrameWorker(UploadTransfer transfer) {
        this.transfer = transfer;
    }

    public synchronized void addProcessor(RecvFrameProcessor processor) {
        Validate.isTrue(state == State.RUNNING);
        // ad processor at the end of queue
        queue.addLast(processor);
        // notify worker thread
        notify();
    }

    public synchronized void terminate() {
        if (state == State.RUNNING) {
            state = State.STOPPING;
            // notify worker thread
            notify();
            // wait until thread terminates
            while (state != State.TERMINATED) {
                try {
                    wait(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                RecvFrameProcessor rfp = getNext();
                if (rfp == null) {
                    break; // terminating worker
                }
                rfp.process();
                if (!transfer.addProcessedFrame(rfp.getSeqNum(), rfp.isLast())) {
                    break; // terminated transfer
                }
                if (rfp.isLast()) {
                    break; // terminate after last frame
                }
            } catch (Throwable t) {
                transfer.frameProcessingFailed(t);
                break;
            }
        }
        synchronized (this) {
            state = State.TERMINATED;
            queue.clear();
        }
    }

    private synchronized RecvFrameProcessor getNext() {
        while (queue.isEmpty() && state == State.RUNNING) {
            try {
                wait();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        if (state != State.RUNNING) {
            return null;
        }
        return queue.getFirst();
    }
}