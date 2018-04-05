package com.lightcomp.ft.common;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

public class ChecksumReadableByteChannel extends ReadableByteChannelWrapper {

    protected final ChecksumGenerator chsg;

    private byte[] buffer = new byte[1024];

    public ChecksumReadableByteChannel(ReadableByteChannel rbch, ChecksumGenerator chsg) {
        super(rbch);
        this.chsg = chsg;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        ByteBuffer ddst = dst.duplicate();
        int n = super.read(dst);
        updateChecksum(ddst, n);
        return n;
    }

    /**
     * @param n
     *            number of bytes read
     */
    private void updateChecksum(ByteBuffer bb, int n) {
        // reallocate buffer if needed
        if (buffer.length < n) {
            buffer = new byte[n];
        }
        // copy buffer
        bb.get(buffer, 0, n);
        chsg.update(buffer, 0, n);
    }
}
