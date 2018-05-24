package com.lightcomp.ft.server.internal;

import com.lightcomp.ft.core.send.FrameBuilder;
import com.lightcomp.ft.core.send.SendFrameContext;

public class DwnldFrameWorker implements Runnable {

    private enum State {
        RUNNING, STOPPING, TERMINATED
    }

    private final DwnldTransfer transfer;

    private final FrameBuilder frameBuilder;

    private State state = State.RUNNING;

    private int frameCount;

    /**
     * @param frameCount
     *            number of prepared frames which is transfer able to accept
     */
    public DwnldFrameWorker(DwnldTransfer transfer, FrameBuilder frameBuilder, int frameCount) {
        this.transfer = transfer;
        this.frameBuilder = frameBuilder;
        this.frameCount = frameCount;
    }

    /**
     * Adds request for next frame to be prepared. Number of prepared frames which is transfer able to
     * accept will be increased.
     * 
     * @return Returns false when worker is terminated.
     */
    public synchronized boolean prepareFrame() {
        if (state == State.RUNNING) {
            frameCount++;
            return true;
        }
        return false;
    }

    public synchronized void terminate() {
        if (state == State.TERMINATED) {
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
                if (state != State.RUNNING || frameCount <= 0) {
                    state = State.TERMINATED;
                    return; // terminated worker
                }
                frameCount--;
            }
            try {
                SendFrameContext frameCtx = frameBuilder.build();
                if (!transfer.framePrepared(frameCtx)) {
                    break; // worker not needed
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
