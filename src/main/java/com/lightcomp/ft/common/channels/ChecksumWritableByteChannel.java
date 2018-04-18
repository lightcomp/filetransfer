package com.lightcomp.ft.common.channels;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

import com.lightcomp.ft.common.ChecksumGenerator;

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