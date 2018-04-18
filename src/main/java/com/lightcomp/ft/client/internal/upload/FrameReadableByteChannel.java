package com.lightcomp.ft.client.internal.upload;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;
import java.util.Collection;
import java.util.Iterator;

class FrameReadableByteChannel implements ReadableByteChannel {

    private final Iterator<FrameBlockData> frameBlockDataIt;

    private ReadableByteChannel currChannel;

    private boolean closed;

    public FrameReadableByteChannel(Collection<FrameBlockData> frameBlockData) {
        this.frameBlockDataIt = frameBlockData.iterator();
    }

    @Override
    public void close() throws IOException {
        closed = true;
        if (currChannel != null) {
            currChannel.close();
            currChannel = null;
        }
    }

    @Override
    public boolean isOpen() {
        return !closed;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        if (closed) {
            throw new ClosedChannelException();
        }
        int num = 0;
        while (true) {
            // set current channel
            if (currChannel == null) {
                if (!frameBlockDataIt.hasNext()) {
                    if (num == 0) {
                        num = -1;
                    }
                    return num; // no more channels
                }
                currChannel = frameBlockDataIt.next().openChannel();
            }
            // read bytes
            int n = currChannel.read(dst);
            if (n > 0) {
                num += n;
                continue; // continue with current channel
            }
            // at this point no byte was read:
            // -> test for full buffer
            if (dst.remaining() == 0) {
                return num;
            }
            // -> channel must be depleted
            currChannel.close();
            currChannel = null;
        }
    }
}