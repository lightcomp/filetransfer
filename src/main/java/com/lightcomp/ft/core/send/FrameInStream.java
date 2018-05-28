package com.lightcomp.ft.core.send;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Iterator;

public class FrameInStream extends InputStream {

    private final Iterator<BlockStreamProvider> bsProviderIt;

    private final DataSendFailureCallback failureCallback;

    private BlockStream currBlockStream;

    private long available;

    private boolean closed;

    public FrameInStream(Collection<BlockStreamProvider> bsProviders, DataSendFailureCallback failureCallback) {
        this.bsProviderIt = bsProviders.iterator();
        this.failureCallback = failureCallback;
        this.available = bsProviders.stream().mapToLong(BlockStreamProvider::getStreamSize).sum();
    }

    @Override
    public int read() throws IOException {
        byte[] b = new byte[1];
        int n = read(b, 0, 1);
        if (n == 1) {
            return b[0];
        }
        return n;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (closed) {
            throw new IOException("Input stream already closed");
        }
        if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        }
        int read;
        try {
            read = readInternal(b, off, len);
        } catch (Throwable t) {
            failureCallback.onDataSendFailed(t);
            throw t;
        }
        // update available if not end of stream (-1)
        if (read > 0) {
            available -= read;
        }
        return read;
    }

    private int readInternal(byte[] b, int off, int len) throws IOException {
        int read = 0;
        while (true) {
            // check if buffer is full
            if (read == len) {
                break;
            }
            // prepare current block data
            if (currBlockStream == null) {
                if (!bsProviderIt.hasNext()) {
                    // no more blocks
                    if (read == 0) {
                        read = -1;
                    }
                    break;
                }
                currBlockStream = bsProviderIt.next().create();
                currBlockStream.open();
            }
            // read block data
            int num = currBlockStream.read(b, off + read, len - read);
            if (num < 0) {
                currBlockStream.close();
                currBlockStream = null;
                continue;
            }
            read += num;
        }
        return read;
    }

    @Override
    public int available() throws IOException {
        if (closed) {
            throw new IOException("Input stream already closed");
        }
        if (available > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) available;
    }

    @Override
    public void close() throws IOException {
        closed = true;
        if (currBlockStream != null) {
            currBlockStream.close();
            currBlockStream = null;
        }
    }
}
