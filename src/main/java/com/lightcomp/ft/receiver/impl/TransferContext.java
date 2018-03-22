package com.lightcomp.ft.receiver.impl;

import com.lightcomp.ft.TransferInfo;
import com.lightcomp.ft.sender.SourceFile;

public interface TransferContext extends TransferInfo {

    void onSourceFileProcessed(SourceFile sourceFile);

    void onFrameSent(String frameId, long size);
}
