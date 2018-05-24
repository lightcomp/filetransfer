package com.lightcomp.ft.core.send;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.common.Checksum;
import com.lightcomp.ft.core.send.items.SourceFile;

public class FileDataStream implements BlockStream {

    private static final Logger logger = LoggerFactory.getLogger(FileDataStream.class);

    private final SourceFile srcFile;

    private final long offset;

    private final long size;

    private final Checksum checksum;

    private final FileDataProgress progress;

    private final Path srcPath;

    private ReadableByteChannel channel;

    private long streamPos;

    public FileDataStream(SourceFile srcFile, long offset, long size, Checksum checksum, FileDataProgress progress,
            Path srcPath) {
        this.srcFile = srcFile;
        this.offset = offset;
        this.size = size;
        this.checksum = checksum;
        this.progress = progress;
        this.srcPath = srcPath;
    }

    @Override
    public void open() throws IOException {
        Validate.isTrue(channel == null);
        channel = srcFile.openChannel(offset);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        }
        if (len == 0) {
            return 0;
        }
        // check if stream ended
        if (size == streamPos) {
            return -1;
        }
        // adjust length by stream remaining bytes
        len = (int) Math.min(size - streamPos, len);
        // read data from source file
        ByteBuffer bb = ByteBuffer.wrap(b, off, len);
        if (channel.read(bb) < len) {
            String message = "Data stream of source file ended prematurely, path=" + srcPath;
            logger.error(message);
            throw new IOException(message);
        }
        // increment position
        streamPos += len;
        // update generator and progress
        long newPos = offset + streamPos;
        checksum.update(newPos, b, off, len);
        progress.update(newPos);
        return len;
    }

    @Override
    public void close() throws IOException {
        if (channel != null) {
            channel.close();
            channel = null;
        }
    }
}
