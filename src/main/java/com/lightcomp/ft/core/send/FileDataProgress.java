package com.lightcomp.ft.core.send;

public class FileDataProgress {

    private final SendProgressInfo progressInfo;

    private long numProcessed;

    public FileDataProgress(SendProgressInfo progressInfo) {
        this.progressInfo = progressInfo;
    }

    public synchronized void update(long newPos) {
        if (newPos <= numProcessed) {
            return;
        }
        long size = newPos - numProcessed;
        numProcessed += size;
        progressInfo.onFileDataSend(size);
    }
}
