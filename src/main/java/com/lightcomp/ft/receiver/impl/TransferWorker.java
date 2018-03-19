package com.lightcomp.ft.receiver.impl;

import com.lightcomp.ft.receiver.TransferState;

class TransferWorker implements Runnable {

    private final Transfer context;

    private final Runnable task;

    private final TransferState nextState;

    public TransferWorker(Transfer context, Runnable task, TransferState nextState) {
        this.context = context;
        this.task = task;
        this.nextState = nextState;
    }

    @Override
    public void run() {
        try {
            task.run();
            context.workerFinished(nextState);
        } catch (Throwable t) {
            context.workerFailed(t);
        }
    }
}
