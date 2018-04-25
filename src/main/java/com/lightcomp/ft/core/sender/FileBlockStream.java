package com.lightcomp.ft.core.sender;

import java.io.IOException;

public interface FileBlockStream {

    long getSize();

    void open() throws IOException;

    int read(byte[] b, int off, int len) throws IOException;

    void close() throws IOException;
}
