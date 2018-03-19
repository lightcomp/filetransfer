package com.lightcomp.ft.sender.impl.phase.frame;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

import com.lightcomp.ft.common.ReadableByteChannelWrapper;

public class ReadLimitingByteChannel extends ReadableByteChannelWrapper {

    private long remainingSize;

    public ReadLimitingByteChannel(ReadableByteChannel rbch, long readLimit) {
        super(rbch);
        this.remainingSize = readLimit;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        if (remainingSize == 0) {
            return 0;
        }
        int remaining = dst.remaining();
        if (remainingSize < remaining) {
            dst.limit((int) remainingSize);
        }
        int size = super.read(dst);
        remainingSize -= size;
        return size;
    }
}