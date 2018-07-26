package com.lightcomp.ft.core.send;

public class FileDataProgress {

    private final SendProgressInfo sendProgressInfo;

    private long numProcessed;

    public FileDataProgress(SendProgressInfo sendProgressInfo) {
        this.sendProgressInfo = sendProgressInfo;
    }

    public synchronized void update(long newPos) {
        if (newPos <= numProcessed) {
            return;
        }
        long size = newPos - numProcessed;
        numProcessed += size;
        sendProgressInfo.onFileDataSend(size);
    }
}
