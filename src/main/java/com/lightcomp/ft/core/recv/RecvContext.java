package com.lightcomp.ft.core.recv;

import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;

public interface RecvContext {

    void setInputChannel(ReadableByteChannel inputChannel);

    Path getCurrentDir();

    Path getCurrentFile();

    void openDir(String name);

    void closeDir();

    void openFile(String name, long size);

    void writeFileData(long offset, long length);

    void closeFile(long lastModified);
}
