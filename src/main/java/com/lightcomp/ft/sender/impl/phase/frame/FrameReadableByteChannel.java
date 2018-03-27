package com.lightcomp.ft.sender.impl.phase.frame;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;
import java.util.Collection;
import java.util.Iterator;

public class FrameReadableByteChannel implements ReadableByteChannel {

    private final Iterator<FrameBlockContext> blockIt;

    private ReadableByteChannel currChannel;

    private boolean closed;

    public FrameReadableByteChannel(Collection<FrameBlockContext> blocks) {
        this.blockIt = blocks.iterator();
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
            if (currChannel == null) {
                if (!blockIt.hasNext()) {
                    if (num == 0) {
                        num = -1;
                    }
                    return num; // no more channels
                }
                currChannel = blockIt.next().openChannel();
            }
            // read bytes
            int n = currChannel.read(dst);
            if (n > 0) {
                num += n;
                continue; // continue with current channel
            }
            // no byte was read, find reason:
            if (dst.remaining() == 0) {
                return num; // full buffer
            }
            // channel at the end
            currChannel.close();
            currChannel = null;
        }
    }
}