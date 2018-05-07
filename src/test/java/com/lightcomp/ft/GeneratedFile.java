package com.lightcomp.ft;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;

import com.lightcomp.ft.core.send.items.SourceDir;
import com.lightcomp.ft.core.send.items.SourceFile;

public class GeneratedFile implements SourceFile {

    private static final byte[] CHARS = { 0x61, 0x62, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69, 0x6a, 0x6b, 0x6c, 0x6d, 0x6e,
            0x6f, 0x70, 0x71, 0x72, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7a };

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
    public byte[] getChecksum() {
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
                long rem = size - pos;
                int len = (int) Math.min(rem, dst.remaining());
                byte[] data = createDataSeq(len);
                dst.put(data, 0, len);
                pos += len;
                return len;
            }
        };
    }

    private static byte[] createDataSeq(int len) {
        byte[] data = new byte[len];
        int cindex = 0;
        for (int i = 0; i < len; i++) {
            data[i] = CHARS[cindex++];
            if (cindex == CHARS.length) {
                cindex = 0;
            }
        }
        return data;
    }
}
