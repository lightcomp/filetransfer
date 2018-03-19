package com.lightcomp.ft.sender.impl.phase;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

import org.apache.commons.lang3.Validate;

import com.lightcomp.ft.common.ChecksumGenerator;
import com.lightcomp.ft.common.ReadableByteChannelWrapper;

public class ChecksumByteChannel extends ReadableByteChannelWrapper {

    private final ChecksumGenerator checksumGenerator;

    private long position;

    private byte[] buffer = new byte[1024];

    public ChecksumByteChannel(SeekableByteChannel sbch, ChecksumGenerator checksumGenerator, long position) {
        super(sbch);
        this.checksumGenerator = checksumGenerator;
        this.position = position;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        int size = super.read(dst);

        // increment channel position
        position += size;

        updateChecksum(dst, size);

        return size;
    }

    @Override
    public void close() throws IOException {
        try {
            // check wrapped channel position
            SeekableByteChannel sbch = (SeekableByteChannel) rbch;
            Validate.isTrue(position == sbch.position());
        } finally {
            super.close();
        }
    }

    private void updateChecksum(ByteBuffer dst, int size) {
        int offset = getChecksumOffset();
        if (offset < 0) {
            return;
        }
        ByteBuffer dstd = dst.duplicate();
        int len = size - offset;
        updateChecksum(dstd, offset, len);
    }

    /**
     * @return Returns negative value if this buffer should be skipped.
     */
    private int getChecksumOffset() {
        if (position <= checksumGenerator.getByteSize()) {
            // bytes already included in checksum
            return -1;
        }
        // calculate buffer offset
        long offset = position - checksumGenerator.getByteSize();
        // for sequential read offset must be in integer range
        return (int) offset;
    }

    private void updateChecksum(ByteBuffer dst, int offset, int len) {
        // reallocate buffer if needed
        if (buffer.length < len) {
            buffer = new byte[len];
        }
        // copy buffer
        dst.flip();
        dst.get(buffer, offset, len);
        checksumGenerator.update(buffer, offset, len);
    }
}
