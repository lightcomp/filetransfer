package com.lightcomp.ft.sender.impl.phase;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import com.lightcomp.ft.common.ChecksumGenerator;
import com.lightcomp.ft.common.ChecksumReadableByteChannel;
import com.lightcomp.ft.common.ChecksumType;
import com.lightcomp.ft.sender.SourceFile;

public class FileProvider {

    private final String fileId;

    private final SourceFile sourceFile;

    private final ChecksumGenerator chsg;

    private String checksum;

    private FileProvider(String fileId, SourceFile sourceFile, ChecksumGenerator checksumGenerator, String checksum) {
        this.fileId = fileId;
        this.sourceFile = sourceFile;
        this.chsg = checksumGenerator;
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
            Validate.isTrue(chsg.getNumProcessed() == getSize());
            // generate file checksum
            checksum = chsg.getHexString();
        }
        return checksum;
    }

    public ReadableByteChannel openChannel(long position) throws IOException {
        try {
            ReadableByteChannel rbch = sourceFile.openChannel(position);
            if (chsg != null) {
                chsg.setStreamPos(position);
                return new ChecksumReadableByteChannel(rbch, chsg);
            }
            return rbch;
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
