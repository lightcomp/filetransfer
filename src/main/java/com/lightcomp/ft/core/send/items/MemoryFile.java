package com.lightcomp.ft.core.send.items;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;

public class MemoryFile implements SourceFile {

    private final String name;

    private final byte[] data;

    private final long lastModified;

    private byte[] checksum;

    public MemoryFile(String name, byte[] data, long lastModified) {
        this.name = name;
        this.data = data;
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
        return data.length;
    }

    @Override
    public long getLastModified() {
        return lastModified;
    }

    @Override
    public byte[] getChecksum() {
        return checksum;
    }

    public void setChecksum(byte[] checksum) {
        this.checksum = checksum;
    }

    @Override
    public ReadableByteChannel openChannel(long position) throws IOException {
        return new ReadableByteChannel() {

            private int pos = (int) position;

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
                if (pos == data.length) {
                    return 0;
                }
                int rem = data.length - pos;
                int len = Math.min(rem, dst.remaining());
                dst.put(data, pos, len);
                pos += len;
                return len;
            }
        };
    }

    public static MemoryFile fromString(String name, long lastModified, String value) {
        byte[] data = value != null ? value.getBytes(StandardCharsets.UTF_8) : null;
        return new MemoryFile(name, data, lastModified);
    }
}
