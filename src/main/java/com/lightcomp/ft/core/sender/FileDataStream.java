package com.lightcomp.ft.core.sender;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;

import org.apache.commons.lang3.Validate;

import com.lightcomp.ft.common.ChecksumGenerator;
import com.lightcomp.ft.core.sender.items.SourceFile;
import com.lightcomp.ft.exception.TransferExceptionBuilder;

class FileDataStream implements FileBlockStream {

    private final SourceFile srcFile;

    private final Path path;

    private final long offset;

    private final long size;

    private final ChecksumGenerator chksmGenerator;

    private ReadableByteChannel rbch;

    private long remaining;

    public FileDataStream(SourceFile srcFile, Path path, long offset, long size, ChecksumGenerator chksmGenerator) {
        this.srcFile = srcFile;
        this.path = path;
        this.offset = offset;
        this.size = size;
        this.chksmGenerator = chksmGenerator;
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public void open() throws IOException {
        Validate.isTrue(rbch == null);
        rbch = srcFile.openChannel(offset);
        remaining = size;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        }
        if (len == 0) {
            return 0;
        }
        if (remaining == 0) {
            return -1;
        }
        // adjust length by remaining bytes
        len = (int) Math.min(remaining, len);
        // read data from source file
        ByteBuffer bb = ByteBuffer.wrap(b, off, len);
        if (rbch.read(bb) < len) {
            throw TransferExceptionBuilder.from("Source file data stream ended prematurely").addParam("path", path).build();
        }
        // update checksum generator if present
        if (chksmGenerator != null) {
            chksmGenerator.update(b, off, len);
        }
        remaining -= len;
        return len;
    }

    @Override
    public void close() throws IOException {
        rbch.close();
        rbch = null;
        remaining = 0;
    }
}
