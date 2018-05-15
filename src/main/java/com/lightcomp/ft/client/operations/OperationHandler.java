package com.lightcomp.ft.client.operations;

public interface OperationHandler {

    /**
     * @return Returns true when operation can be recovered.
     */
    boolean prepareRecovery(boolean interruptible);

    void recoverySucceeded();
}
