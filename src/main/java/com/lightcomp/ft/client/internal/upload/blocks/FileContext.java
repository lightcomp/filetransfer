package com.lightcomp.ft.client.internal.upload.blocks;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import com.lightcomp.ft.client.SourceFile;
import com.lightcomp.ft.client.internal.upload.frame.DataProvider;
import com.lightcomp.ft.common.BoundedReadableByteChannel;
import com.lightcomp.ft.common.ChecksumGenerator;
import com.lightcomp.ft.common.ChecksumReadableByteChannel;
import com.lightcomp.ft.common.ChecksumType;

class FileContext {

    private final String itemId;

    private final String parentItemId;

    private final SourceFile srcFile;

    private final ChecksumGenerator chsg;

    private String checksum;

    private FileContext(String itemId, String parentItemId, SourceFile srcFile, ChecksumGenerator chsg, String checksum) {
        this.itemId = itemId;
        this.parentItemId = parentItemId;
        this.srcFile = srcFile;
        this.chsg = chsg;
        this.checksum = checksum;
    }

    public String getItemId() {
        return itemId;
    }

    public String getParentItemId() {
        return parentItemId;
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
            Validate.isTrue(chsg.getNumProcessed() == srcFile.getSize());
            // generate file checksum
            checksum = chsg.generate();
        }
        return checksum;
    }

    public DataProvider getDataProvider(long offset, long size) {
        return new DataProvider() {
            @Override
            public long getSize() {
                return size;
            }

            @Override
            public ReadableByteChannel openChannel() throws IOException {
                ReadableByteChannel rbch = FileContext.this.openChannel(offset);
                return new BoundedReadableByteChannel(rbch, size);
            }
        };
    }

    private ReadableByteChannel openChannel(long position) throws IOException {
        try {
            ReadableByteChannel rbch = srcFile.openChannel(position);
            if (chsg != null) {
                chsg.setStreamPos(position);
                rbch = new ChecksumReadableByteChannel(rbch, chsg);
            }
            return rbch;
        } catch (IOException e) {
            throw new IOException("Failed to open source file, name:" + srcFile.getName(), e);
        }
    }

    public static FileContext create(String itemId, String parentItemId, SourceFile srcFile) {
        String checksum = srcFile.getChecksum();
        if (StringUtils.isNotEmpty(checksum)) {
            return new FileContext(itemId, parentItemId, srcFile, null, checksum);
        }
        ChecksumGenerator chsg = new ChecksumGenerator(ChecksumType.SHA_512);
        return new FileContext(itemId, parentItemId, srcFile, chsg, null);
    }
}
