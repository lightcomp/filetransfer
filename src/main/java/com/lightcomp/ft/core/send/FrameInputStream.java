package com.lightcomp.ft.core.send;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Iterator;

public class FrameInputStream extends InputStream {

    private final Iterator<FrameBlockStream> blockStreamIt;

    private FrameBlockStream currStream;

    private long available;

    private boolean closed;

    public FrameInputStream(Collection<FrameBlockStream> blockStreams) {
        this.blockStreamIt = blockStreams.iterator();
        this.available = blockStreams.stream().mapToLong(FrameBlockStream::getSize).sum();
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
        int read = 0;
        while (true) {
            // check if buffer is full
            if (read == len) {
                break;
            }
            // prepare current block data
            if (currStream == null) {
                if (!blockStreamIt.hasNext()) {
                    // no more blocks
                    if (read == 0) {
                        read = -1;
                    }
                    break;
                }
                currStream = blockStreamIt.next();
                currStream.open();
            }
            // read block data
            int num = currStream.read(b, off + read, len - read);
            if (num < 0) {
                currStream.close();
                currStream = null;
                continue;
            }
            read += num;
        }
        if (read > 0) {
            available -= read;
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
        if (currStream != null) {
            currStream.close();
            currStream = null;
        }
    }
}
