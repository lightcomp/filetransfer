package com.lightcomp.ft.core.send;

import java.io.IOException;

/**
 * Provides data for single frame block. Simplified interface used as a input
 * stream.
 */
public interface FrameBlockStream {

    long getSize();

    void open() throws IOException;

    int read(byte[] b, int off, int len) throws IOException;

    void close() throws IOException;
}
