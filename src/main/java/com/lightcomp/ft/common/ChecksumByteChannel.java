package com.lightcomp.ft.common;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

public class ChecksumByteChannel implements WritableByteChannel {

    private final WritableByteChannel wbch;

    private final Checksum checksum;

    private long position;

    private byte[] buffer = new byte[256];

    public ChecksumByteChannel(WritableByteChannel wbch, Checksum checksum, long position) {
        this.wbch = wbch;
        this.checksum = checksum;
        this.position = position;
    }

    @Override
    public boolean isOpen() {
        return wbch.isOpen();
    }

    @Override
    public void close() throws IOException {
        buffer = null;
        wbch.close();
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        // duplicate buffer (not data) for checksum update
        ByteBuffer dsrc = src.duplicate();
        // write to original channel
        int n = wbch.write(src);
        // update checksum
        updateChecksum(dsrc, n);
        // increment position
        position += n;
        return n;
    }

    private void updateChecksum(ByteBuffer bb, int len) {
        // reallocate buffer if needed
        if (buffer.length < len) {
            buffer = new byte[len];
        }
        // copy buffer
        bb.get(buffer, 0, len);
        // update checksum
        checksum.update(position, buffer, 0, len);
    }
}
