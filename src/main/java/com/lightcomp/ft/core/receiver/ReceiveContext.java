package com.lightcomp.ft.core.receiver;

public interface ReceiveContext {

    void openDir(String name);

    void closeDir();

    void openFile(String name, long size);

    void writeFileData(long offset, long length);

    void closeFile(long lastModified);
}
