package com.lightcomp.ft.sender.impl.phase.frame;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

import com.lightcomp.ft.common.ReadableByteChannelWrapper;

public class ReadLimitingByteChannel extends ReadableByteChannelWrapper {

    private long remainingBytes;

    public ReadLimitingByteChannel(ReadableByteChannel rbch, long lengthLimit) {
        super(rbch);
        this.remainingBytes = lengthLimit;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        if (remainingBytes == 0) {
            return -1;
        }
        int num = readInternal(dst);
        remainingBytes -= num;
        return num;
    }

    private int readInternal(ByteBuffer dst) throws IOException {
        long diff = dst.remaining() - remainingBytes;
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