package com.lightcomp.ft.common.channels;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

public class BoundedReadableByteChannel extends ReadableByteChannelWrapper {

    private long remainingSize;

    public BoundedReadableByteChannel(ReadableByteChannel rbch, long maxSize) {
        super(rbch);
        this.remainingSize = maxSize;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        if (remainingSize == 0) {
            return -1;
        }
        int n = readInternal(dst);
        remainingSize -= n;
        return n;
    }

    private int readInternal(ByteBuffer dst) throws IOException {
        long diff = dst.remaining() - remainingSize;
        if (diff <= 0) {
            return super.read(dst);
        }
        int origLimit = dst.limit();
        int newLimit = origLimit - (int) diff;
        dst.limit(newLimit);
        try {
            return super.read(dst);
        } finally {
            dst.limit(origLimit);
        }
    }
}