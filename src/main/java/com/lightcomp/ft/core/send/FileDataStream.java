package com.lightcomp.ft.core.send;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.common.ChecksumGenerator;
import com.lightcomp.ft.core.send.items.SourceFile;

class FileDataStream implements FrameBlockStream {

    private static final Logger logger = LoggerFactory.getLogger(FileDataStream.class);
    
    private final SourceFile srcFile;

    private final long offset;

    private final long size;

    private final ChecksumGenerator chksmGenerator;

    private final SendProgressInfo progressInfo;

    private final Path logPath;

    private ReadableByteChannel channel;

    private long remaining;

    public FileDataStream(SourceFile srcFile, long offset, long size, ChecksumGenerator chksmGenerator,
            SendProgressInfo progressInfo, Path logPath) {
        this.srcFile = srcFile;
        this.offset = offset;
        this.size = size;
        this.chksmGenerator = chksmGenerator;
        this.progressInfo = progressInfo;
        this.logPath = logPath;
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public void open() throws IOException {
        Validate.isTrue(channel == null);
        channel = srcFile.openChannel(offset);
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
        if (channel.read(bb) < len) {
            String msg = "Source file data stream ended prematurely, path=" + logPath;
            logger.error(msg);
            throw new IOException(msg);
        }
        // update checksum generator if present
        if (chksmGenerator != null) {
            chksmGenerator.update(b, off, len);
        }
        remaining -= len;
        // report progress
        progressInfo.onDataSend(len);
        return len;
    }

    @Override
    public void close() throws IOException {
        if (channel != null) {
            channel.close();
            channel = null;
        }
        remaining = 0;
    }
}
