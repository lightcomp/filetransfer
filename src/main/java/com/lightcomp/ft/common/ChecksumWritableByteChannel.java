package com.lightcomp.ft.common;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

public class ChecksumWritableByteChannel extends WritableByteChannelWrapper {

    protected final ChecksumGenerator chsg;

    private byte[] buffer = new byte[1024];

    public ChecksumWritableByteChannel(WritableByteChannel wbch, ChecksumGenerator chsg) {
        super(wbch);
        this.chsg = chsg;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        ByteBuffer dsrc = src.duplicate();
        int n = super.write(src);
        updateChecksum(dsrc, n);
        return n;
    }

    private void updateChecksum(ByteBuffer bb, int len) {
        // reallocate buffer if needed
        if (buffer.length < len) {
            buffer = new byte[len];
        }
        // copy buffer
        bb.get(buffer, 0, len);
        chsg.update(buffer, 0, len);
    }
}
