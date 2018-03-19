package com.lightcomp.ft.common;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

public class ReadableByteChannelWrapper implements ReadableByteChannel {

    protected final ReadableByteChannel rbch;

    public ReadableByteChannelWrapper(ReadableByteChannel rbch) {
        this.rbch = rbch;
    }

    @Override
    public void close() throws IOException {
        rbch.close();
    }

    @Override
    public boolean isOpen() {
        return rbch.isOpen();
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        return rbch.read(dst);
    }
}
