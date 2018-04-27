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

    private final LinkedList<RecvFrameProcessor> processorQueue = new LinkedList<>();

    private final UploadTransfer transfer;

    private State state = State.RUNNING;

    public UploadFrameWorker(UploadTransfer transfer) {
        this.transfer = transfer;
    }

    public synchronized void addProcessor(RecvFrameProcessor processor) {
        Validate.isTrue(state == State.RUNNING);

        processorQueue.addLast(processor);
        // notify worker thread
        notify();
    }

    public synchronized void terminate() {
        if (state == State.RUNNING) {
            state = State.STOPPING;
            // notify worker thread
            notify();
            // wait until worker thread terminates
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
                RecvFrameProcessor rfp = getNextProcessor();
                if (rfp == null) {
                    break; // stopping
                }
                rfp.process();
                // notify upload
                transfer.onFrameFinished(rfp);
                // terminate if last
                if (rfp.isLast()) {
                    break;
                }
            } catch (Throwable t) {
                transfer.transferFailed(t);
                break;
            }
        }
        synchronized (this) {
            state = State.TERMINATED;
            // notify stopping threads
            notifyAll();
        }
    }

    private synchronized RecvFrameProcessor getNextProcessor() {
        while (processorQueue.isEmpty() && state == State.RUNNING) {
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        if (state != State.RUNNING) {
            return null; // stopping
        }
        return processorQueue.getFirst();
    }
}