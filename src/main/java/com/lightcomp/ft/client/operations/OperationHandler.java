package com.lightcomp.ft.client.operations;

import com.lightcomp.ft.core.TransferInfo;

public interface OperationHandler extends TransferInfo {

    /**
     * @return true when operation can be recovered
     */
    boolean prepareRecovery();
}
