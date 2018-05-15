package com.lightcomp.ft.common;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

public class ChecksumByteChannel implements WritableByteChannel {

    private final WritableByteChannel wbch;

    private final ChecksumGenerator chksmGenerator;

    private byte[] buffer = new byte[1024];

    public ChecksumByteChannel(WritableByteChannel wbch, ChecksumGenerator chksmGenerator) {
        this.wbch = wbch;
        this.chksmGenerator = chksmGenerator;
    }

    @Override
    public boolean isOpen() {
        return wbch.isOpen();
    }

    @Override
    public void close() throws IOException {
        buffer = null;
        wbch.close();
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        ByteBuffer dsrc = src.duplicate();
        int n = wbch.write(src);
        updateChecksum(dsrc, n);
        return n;
    }

    private void updateChecksum(ByteBuffer bb, int len) {
        // reallocate buffer if needed
        if (buffer.length < len) {
            buffer = new byte[len];
        }
        // copy buffer
        bb.get(buffer, 0, len);
        chksmGenerator.update(buffer, 0, len);
    }
}
