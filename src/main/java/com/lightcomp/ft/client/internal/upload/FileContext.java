package com.lightcomp.ft.client.internal.upload;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import com.lightcomp.ft.common.ChecksumGenerator;
import com.lightcomp.ft.common.channels.BoundedReadableByteChannel;
import com.lightcomp.ft.common.channels.ChecksumReadableByteChannel;
import com.lightcomp.ft.core.SourceFile;

public class FileContext {

    private final SourceFile srcFile;

    private final String relativePath;

    private final ChecksumGenerator checksumGenerator;

    private String checksum;

    private FileContext(SourceFile srcFile, String relativePath, ChecksumGenerator checksumGenerator, String checksum) {
        this.srcFile = srcFile;
        this.relativePath = relativePath;
        this.checksumGenerator = checksumGenerator;
        this.checksum = checksum;
    }

    public String getName() {
        return srcFile.getName();
    }

    public long getSize() {
        return srcFile.getSize();
    }

    public long getLastModified() {
        return srcFile.getLastModified();
    }

    public String getChecksum() {
        if (checksum == null) {
            // check if generator is complete
            Validate.isTrue(checksumGenerator.getNumProcessed() == srcFile.getSize());
            // generate file checksum
            checksum = checksumGenerator.generate();
        }
        return checksum;
    }

    public FrameBlockData createFrameBlockData(long offset, long size) {
        return () -> {
            ReadableByteChannel rbch = openChannel(offset);
            return new BoundedReadableByteChannel(rbch, size);
        };
    }

    private ReadableByteChannel openChannel(long position) throws IOException {
        try {
            ReadableByteChannel rbch = srcFile.openChannel(position);
            if (checksumGenerator != null) {
                checksumGenerator.setStreamPos(position);
                rbch = new ChecksumReadableByteChannel(rbch, checksumGenerator);
            }
            return rbch;
        } catch (IOException e) {
            throw new IOException("Failed to open source file, path:" + relativePath, e);
        }
    }

    public static FileContext create(SourceFile srcFile, String relativePath) {
        ChecksumGenerator chg = null;
        String ch = srcFile.getChecksum();
        if (StringUtils.isEmpty(ch)) {
            chg = ChecksumGenerator.createDefault();
            ch = null;
        }
        return new FileContext(srcFile, relativePath, chg, ch);
    }
}
