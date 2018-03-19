package com.lightcomp.ft.sender.impl.phase.frame;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;
import java.util.Collection;
import java.util.Iterator;

import org.apache.commons.lang3.Validate;

public class FrameReadableByteChannel implements ReadableByteChannel {

    private final Iterator<FrameBlockContext> blockCtxIt;

    private ReadableByteChannel openChannel;

    private boolean closed;

    public FrameReadableByteChannel(Collection<FrameBlockContext> blockCtxList) {
        this.blockCtxIt = blockCtxList.iterator();
        Validate.isTrue(blockCtxIt.hasNext());
    }

    @Override
    public void close() throws IOException {
        closed = true;
        if (openChannel != null) {
            openChannel.close();
            openChannel = null;
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
        // prepare first channel
        if (openChannel == null) {
            openChannel = blockCtxIt.next().openChannel();
        }
        // reads channels until buffer reaches limit
        int size = 0;
        while (dst.remaining() > 0) {
            int b = openChannel.read(dst);
            if (b > 0) {
                size += b;
                continue;
            }
            if (!blockCtxIt.hasNext()) {
                break;
            }
            openChannel = blockCtxIt.next().openChannel();
        }
        return size;
    }
}