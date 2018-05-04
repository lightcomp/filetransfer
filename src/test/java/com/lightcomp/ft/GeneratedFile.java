package com.lightcomp.ft;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;
import java.util.Arrays;

import com.lightcomp.ft.core.send.items.SourceDir;
import com.lightcomp.ft.core.send.items.SourceFile;

public class GeneratedFile implements SourceFile {

    private final String name;

    private final long size;

    private final long lastModified;

    public GeneratedFile(String name, long size, long lastModified) {
        this.name = name;
        this.size = size;
        this.lastModified = lastModified;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isDir() {
        return false;
    }

    @Override
    public SourceDir asDir() {
        throw new UnsupportedOperationException();
    }

    @Override
    public SourceFile asFile() {
        return this;
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public long getLastModified() {
        return lastModified;
    }

    @Override
    public String getChecksum() {
        return null;
    }

    @Override
    public ReadableByteChannel openChannel(long position) throws IOException {
        return new ReadableByteChannel() {

            private long pos = (int) position;

            @Override
            public void close() throws IOException {
                pos = -1;
            }

            @Override
            public boolean isOpen() {
                return pos >= 0;
            }

            @Override
            public int read(ByteBuffer dst) throws IOException {
                if (pos < 0) {
                    throw new ClosedChannelException();
                }
                if (pos == size) {
                    return 0;
                }
                int len = (int) Math.min(size - pos, dst.remaining());
                byte[] data = new byte[len];
                Arrays.fill(data, (byte) 0x41); // fills with A char
                dst.put(data, 0, len);
                pos += len;
                return len;
            }
        };
    }
}
