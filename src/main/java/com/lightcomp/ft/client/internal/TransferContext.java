package com.lightcomp.ft.client.internal;

import com.lightcomp.ft.TransferInfo;
import com.lightcomp.ft.client.ClientConfig;

import cxf.FileTransferService;

public interface TransferContext extends TransferInfo {

    ClientConfig getConfig();

    FileTransferService getService();

    void sleep(long ms);

    void onTransferRecovery();
}
