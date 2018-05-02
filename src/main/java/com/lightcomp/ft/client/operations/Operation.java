package com.lightcomp.ft.client.operations;

import com.lightcomp.ft.wsdl.v1.FileTransferService;

public interface Operation {

    boolean isInterruptible();

    int getRecoveryCount();

    boolean execute(FileTransferService client);
}
