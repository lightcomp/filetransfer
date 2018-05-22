package com.lightcomp.ft.core.send;

public class FileDataProgress {

    private final SendProgressInfo progressInfo;

    private long numProcessed;

    public FileDataProgress(SendProgressInfo progressInfo) {
        this.progressInfo = progressInfo;
    }

    public synchronized void update(long position) {
        if (position <= numProcessed) {
            return;
        }
        long size = position - numProcessed;
        numProcessed += size;
        progressInfo.onDataSend(size);
    }
}
