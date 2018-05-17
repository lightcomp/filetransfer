package com.lightcomp.ft.client.operations;

public interface OperationHandler {

    String getTransferId();

    /**
     * @return true when operation can be recovered
     */
    boolean prepareRecovery();

    void recoverySucceeded();
}
