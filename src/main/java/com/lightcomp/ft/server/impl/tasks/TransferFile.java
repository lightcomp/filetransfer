package com.lightcomp.ft.server.impl.tasks;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;

import org.apache.commons.lang3.Validate;

import com.lightcomp.ft.common.ChecksumGenerator;
import com.lightcomp.ft.common.ChecksumType;
import com.lightcomp.ft.common.ChecksumWritableByteChannel;
import com.lightcomp.ft.exception.TransferExceptionBuilder;

public class TransferFile {

    private static final int BUFFER_SIZE = 65536;

    private final String fileId;

    private final Path path;

    private final long size;

    private final long lastModified;

    private ChecksumGenerator chsg;

    private String checksum;

    public TransferFile(String fileId, Path path, long size, long lastModified, ChecksumType checksumType) {
        this.fileId = fileId;
        this.path = path;
        this.size = size;
        this.lastModified = lastModified;
        this.chsg = new ChecksumGenerator(checksumType);
    }

    public long getTransferedSize() {
        return chsg.getNumProcessed();
    }

    public boolean isTransfered() {
        return chsg.getNumProcessed() == size;
    }

    public String getFileId() {
        return fileId;
    }

    public long getSize() {
        return size;
    }

    public String getChecksum() {
        if (!isTransfered()) {
            throw TransferExceptionBuilder.from("Cannot generate checksum for partial file").addParam("fileId", fileId)
                    .addParam("size", size).addParam("transferedSize", chsg.getNumProcessed()).build();
        }
        if (checksum == null) {
            checksum = chsg.generate();
        }
        return checksum;
    }

    public void writeChunk(InputStream is, long offset, long length) {
        long transferedSize = chsg.getNumProcessed();
        if (transferedSize != offset) {
            throw TransferExceptionBuilder.from("File must be written sequentially").addParam("fileId", fileId)
                    .addParam("fileOffset", offset).addParam("transferedSize", transferedSize).build();
        }
        long newSize = offset + length;
        if (newSize > size) {
            throw TransferExceptionBuilder.from("File chunk overlaps total size").addParam("fileId", fileId)
                    .addParam("fileSize", size).addParam("newSize", newSize).build();
        }
        if (length == 0) {
            return;
        }
        // prepare open option
        OpenOption[] openOptions = new OpenOption[] { StandardOpenOption.WRITE,
                offset > 0 ? StandardOpenOption.APPEND : StandardOpenOption.CREATE_NEW };
        // open file for write at the end
        try (SeekableByteChannel sbch = Files.newByteChannel(path, openOptions)) {
            Validate.isTrue(sbch.position() == offset);
            // wrap channel for checksum calculating
            try (WritableByteChannel wbch = new ChecksumWritableByteChannel(sbch, chsg)) {
                writeChunk(is, wbch, length);

            }
        } catch (IOException e) {
            throw TransferExceptionBuilder.from("Failed to open destination file").addParam("fileId", fileId)
                    .addParam("path", path).setCause(e).build();
        }
        if (isTransfered()) {
            finalizeFile();
        }
    }

    private void writeChunk(InputStream is, WritableByteChannel wbch, long remaining) {
        byte[] buffer = new byte[BUFFER_SIZE];
        int numR, numW;

        while (true) {
            int length = remaining < BUFFER_SIZE ? (int) remaining : BUFFER_SIZE;
            try {
                numR = is.read(buffer, 0, length);
            } catch (IOException e) {
                throw TransferExceptionBuilder.from("Failed to read file chunk data stream").addParam("fileId", fileId)
                        .setCause(e).build();
            }
            if (numR <= 0) {
                break;
            }
            // create buffer wrapper for channel NIO API
            ByteBuffer bb = ByteBuffer.wrap(buffer, 0, numR);
            try {
                numW = wbch.write(bb);
            } catch (IOException e) {
                throw TransferExceptionBuilder.from("Failed to write file chunk").addParam("fileId", fileId).setCause(e).build();
            }
            Validate.isTrue(numR == numW);
            // update remaining bytes
            remaining -= numW;
        }
        if (remaining > 0) {
            throw TransferExceptionBuilder.from("File chunk data stream ended prematurely").addParam("fileId", fileId)
                    .addParam("remainingBytes", remaining).build();
        }
    }

    private void finalizeFile() {
        // generate checksum
        checksum = chsg.generate();
        chsg = null;
        // set last modified
        FileTime lm = FileTime.fromMillis(lastModified);
        try {
            Files.setLastModifiedTime(path, lm);
        } catch (IOException e) {
            throw TransferExceptionBuilder.from("Failed to finalize file").addParam("fileId", fileId).setCause(e).build();
        }
    }
}
