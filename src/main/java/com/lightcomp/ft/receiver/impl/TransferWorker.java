package com.lightcomp.ft.receiver.impl;

import com.lightcomp.ft.exception.CanceledException;
import com.lightcomp.ft.receiver.TransferState;
import com.lightcomp.ft.receiver.impl.tasks.Task;

class TransferWorker implements Runnable {

    private final TransferImpl transfer;

    private final Task task;

    private final TransferState nextState;

    public TransferWorker(TransferImpl transfer, Task task, TransferState nextState) {
        this.transfer = transfer;
        this.task = task;
        this.nextState = nextState;
    }

    @Override
    public void run() {
        try {
            if (transfer.isCancelPending()) {
                throw new CanceledException();
            }
            task.run();
            transfer.workerFinished(nextState);
        } catch (Throwable t) {
            transfer.workerFailed(t);
        }
    }
}
