package com.lightcomp.ft.client.internal.operations;

public interface OperationHandler {

    String getTransferId();

    /**
     * @return true when operation can be recovered
     */
    boolean prepareRecovery();

    void recoverySucceeded();
}
