package com.lightcomp.ft.server.internal;

import com.lightcomp.ft.core.send.FrameBuilder;
import com.lightcomp.ft.core.send.SendFrameContext;

public class DwnldFrameWorker implements Runnable {

    private enum State {
        RUNNING, STOPPING, TERMINATED, FINISHED
    }

    private final DwnldTransfer transfer;

    private final FrameBuilder frameBuilder;

    private State state = State.RUNNING;

    private int frameCounter;

    public DwnldFrameWorker(DwnldTransfer transfer, FrameBuilder frameBuilder) {
        this.transfer = transfer;
        this.frameBuilder = frameBuilder;
    }

    /**
     * Adds request for next frame to be prepared.
     * 
     * @return Returns false when worker is finished.
     */
    public synchronized boolean prepareFrame() {
        if (state == State.RUNNING) {
            frameCounter++;
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
            synchronized (this) {
                if (state != State.RUNNING) {
                    state = State.TERMINATED;
                    return; // worker is stopping
                }
                if (frameCounter <= 0) {
                    state = State.FINISHED;
                    return; // no more frames
                }
                frameCounter--;
            }
            try {
                SendFrameContext frameCtx = frameBuilder.build();
                if (!transfer.addPreparedFrame(frameCtx)) {
                    break; // rejected frame
                }
            } catch (Throwable t) {
                ErrorContext ec = new ErrorContext("Failed to build download frame", transfer)
                        .addParam("seqNum", frameBuilder.getCurrentSeqNum()).setCause(t);
                transfer.transferFailed(ec);
                break; // builder failed
            }
        }
        synchronized (this) {
            state = State.TERMINATED;
        }
    }
}
