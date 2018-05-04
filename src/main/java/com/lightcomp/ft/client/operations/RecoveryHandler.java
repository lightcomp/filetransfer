package com.lightcomp.ft.client.operations;

public interface RecoveryHandler {

    /**
     * @return Returns true when given operation can be executed in recovery mode.
     */
    boolean prepareRecovery(boolean interruptible);
    
    void onRecoverySuccess();
}
