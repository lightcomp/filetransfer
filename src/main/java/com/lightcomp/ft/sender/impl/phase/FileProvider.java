package com.lightcomp.ft.sender.impl.phase;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import com.lightcomp.ft.common.ChecksumGenerator;
import com.lightcomp.ft.common.ChecksumType;
import com.lightcomp.ft.sender.SourceFile;

public class FileProvider {

    private final String fileId;

    private final SourceFile sourceFile;

    private final ChecksumGenerator checksumGenerator;

    private String checksum;

    private FileProvider(String fileId, SourceFile sourceFile, ChecksumGenerator checksumGenerator, String checksum) {
        this.fileId = fileId;
        this.sourceFile = sourceFile;
        this.checksumGenerator = checksumGenerator;
        this.checksum = checksum;
    }

    public String getFileId() {
        return fileId;
    }

    public long getSize() {
        return sourceFile.getSize();
    }

    public String getChecksum() {
        if (checksum == null) {
            // check if generator is complete
            Validate.isTrue(checksumGenerator.getByteSize() == getSize());
            // generate file checksum
            checksum = checksumGenerator.generate();
        }
        return checksum;
    }

    public ReadableByteChannel openChannel(long position) throws IOException {
        try {
            SeekableByteChannel sbch = sourceFile.openChannel();
            sbch.position(position);
            if (checksumGenerator != null) {
                return new ChecksumByteChannel(sbch, checksumGenerator, position);
            }
            return sbch;
        } catch (IOException e) {
            throw new IOException("Failed to open source file, name:" + sourceFile.getName(), e);
        }
    }

    public static FileProvider create(String fileId, SourceFile sourceFile, ChecksumType checksumType) {
        String checksum = sourceFile.getChecksum();
        if (StringUtils.isEmpty(checksum)) {
            ChecksumGenerator checksumGenerator = new ChecksumGenerator(checksumType);
            return new FileProvider(fileId, sourceFile, checksumGenerator, null);
        }
        return new FileProvider(fileId, sourceFile, null, checksum);
    }
}
