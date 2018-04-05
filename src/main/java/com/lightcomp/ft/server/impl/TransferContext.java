package com.lightcomp.ft.server.impl;

import java.nio.file.Path;

import com.lightcomp.ft.TransferInfo;
import com.lightcomp.ft.server.impl.tasks.TransferFile;

public interface TransferContext extends TransferInfo {

    Path getTransferDir();

    int getFileCount();
    
    void addFile(TransferFile file);

    TransferFile getFile(String fileId);

    void onFrameSent(String frameId, long size);
}
