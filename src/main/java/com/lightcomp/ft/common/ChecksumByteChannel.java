package com.lightcomp.ft.common;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

public class ChecksumByteChannel implements WritableByteChannel {

    private final WritableByteChannel wbch;

    private final ChecksumGenerator chksmGenerator;

    private byte[] dataBuffer = new byte[1024];

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
        dataBuffer = null;
        wbch.close();
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        ByteBuffer dsrc = src.duplicate();
        int n = wbch.write(src);
        updateChecksum(dsrc, n);
        return n;
    }

    /**
     * @param n
     *            number of bytes read
     */
    private void updateChecksum(ByteBuffer bb, int n) {
        // reallocate buffer if needed
        if (dataBuffer.length < n) {
            dataBuffer = new byte[n];
        }
        // copy buffer
        bb.get(dataBuffer, 0, n);
        chksmGenerator.update(dataBuffer, 0, n);
    }
}
