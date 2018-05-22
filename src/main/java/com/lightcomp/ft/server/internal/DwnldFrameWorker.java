package com.lightcomp.ft.server.internal;

import java.util.Iterator;
import java.util.LinkedList;

import com.lightcomp.ft.core.send.FrameBlockBuilder;
import com.lightcomp.ft.core.send.SendFrameContext;
import com.lightcomp.ft.core.send.SendFrameContextImpl;
import com.lightcomp.ft.core.send.items.SourceItem;
import com.lightcomp.ft.server.ServerConfig;

public class DwnldFrameWorker implements Runnable {

    private enum State {
        RUNNING, STOPPING, TERMINATED, FINISHED
    }

    public static final int MAX_PREPARED_FRAMES = 3;

    private final LinkedList<SendFrameContext> preparedFrames = new LinkedList<>();

    private final DownloadTransfer transfer;

    private final ServerConfig config;

    private final FrameBlockBuilder fbBuilder;

    private State state = State.RUNNING;

    private int currSeqNum;

    public DwnldFrameWorker(DownloadTransfer transfer, ServerConfig config, Iterator<SourceItem> itemIt) {
        this.transfer = transfer;
        this.config = config;
        this.fbBuilder = new FrameBlockBuilder(itemIt, transfer);
    }

    public synchronized boolean isFinished() {
        return state == State.FINISHED;
    }

    /**
     * @return Next context of prepared frame. Can be null when frame is not yet prepared or worker
     *         finished.
     */
    public synchronized SendFrameContext getNextFrame() {
        if (state == State.RUNNING || state == State.FINISHED) {
            if (preparedFrames.isEmpty()) {
                return null;
            }
            return preparedFrames.getFirst();
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
            SendFrameContext frameCtx;
            try {
                currSeqNum++;
                frameCtx = new SendFrameContextImpl(currSeqNum, config.getMaxFrameBlocks(), config.getMaxFrameSize());
                fbBuilder.build(frameCtx);
            } catch (Throwable t) {
                ErrorContext ec = new ErrorContext("Failed to build download frame", transfer)
                        .addParam("seqNum", currSeqNum).setCause(t);
                transfer.transferFailed(ec);
                break; // builder failed
            }
            synchronized (this) {
                if (state != State.RUNNING) {
                    break; // worker is stopping
                }
                preparedFrames.addLast(frameCtx);
                // check if last frame or maximum reached
                if (frameCtx.isLast() || preparedFrames.size() >= MAX_PREPARED_FRAMES) {
                    state = State.FINISHED;
                    return;
                }
            }
        }
        synchronized (this) {
            state = State.TERMINATED;
            preparedFrames.clear();
        }
    }
}
