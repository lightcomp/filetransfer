package com.lightcomp.ft.receiver.impl;

import java.nio.file.Path;

import com.lightcomp.ft.TransferInfo;
import com.lightcomp.ft.receiver.impl.tasks.TransferFile;

public interface TransferContext extends TransferInfo {

    Path getTransferDir();

    int getFileCount();
    
    void addFile(TransferFile file);

    TransferFile getFile(String fileId);

    void onFrameSent(String frameId, long size);
}
