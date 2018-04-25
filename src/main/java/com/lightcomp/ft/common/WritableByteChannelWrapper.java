package com.lightcomp.ft.common;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

public class WritableByteChannelWrapper implements WritableByteChannel {

    protected final WritableByteChannel wbch;

    public WritableByteChannelWrapper(WritableByteChannel wbch) {
        this.wbch = wbch;
    }

    @Override
    public void close() throws IOException {
        wbch.close();
    }

    @Override
    public boolean isOpen() {
        return wbch.isOpen();
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        return wbch.write(src);
    }
}
