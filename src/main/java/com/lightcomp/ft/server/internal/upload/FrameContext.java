package com.lightcomp.ft.server.internal.upload;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

import com.lightcomp.ft.xsd.v1.FrameBlock;

public interface FrameContext {

    int getSeqNum();

    long getDataSize();

    boolean isLast();

    Collection<FrameBlock> getBlocks();

    InputStream openDataInputStream() throws IOException;

    void openDir(String name);

    void closeDir();

    void openFile(String name, long size);

    void closeFile(String checksum, long lastModified);

    void writeFileData(InputStream is, long frameOffset, long offset, long size);
}
