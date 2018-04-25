package com.lightcomp.ft.server.internal.upload;

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
import com.lightcomp.ft.common.ChecksumWritableByteChannel;
import com.lightcomp.ft.exception.TransferExceptionBuilder;

public class TransferFile {

    private static final int BUFFER_SIZE = 65536;

    private final Path path;

    private final long size;

    private final ChecksumGenerator chksmGenerator;

    private byte[] chksm;

    public TransferFile(Path path, long size) {
        this.path = path;
        this.size = size;
        this.chksmGenerator = ChecksumGenerator.create();
    }

    public Path getPath() {
        return path;
    }

    public long getSize() {
        return size;
    }

    public long getTransferedSize() {
        return chksmGenerator.getNumProcessed();
    }

    public void writeData(InputStream is, long offset, long length) {
        if (offset != getTransferedSize()) {
            throw TransferExceptionBuilder.from("File must be written in sequence").addParam("path", path)
                    .addParam("frameOffset", offset).addParam("transferedSize", getTransferedSize()).build();
        }
        long newSize = offset + length;
        if (newSize > size) {
            throw TransferExceptionBuilder.from("Data overlaps file size").addParam("path", path).build();
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
            try (WritableByteChannel wbch = new ChecksumWritableByteChannel(sbch, chksmGenerator)) {
                writeData(is, wbch, length);
            }
        } catch (IOException e) {
            throw TransferExceptionBuilder.from("Failed to open target file").addParam("path", path).setCause(e).build();
        }
    }

    private void writeData(InputStream is, WritableByteChannel wbch, long length) {
        byte[] buffer = new byte[BUFFER_SIZE];
        int numR, numW;

        while (true) {
            int lenR = length < BUFFER_SIZE ? (int) length : BUFFER_SIZE;
            try {
                numR = is.read(buffer, 0, lenR);
            } catch (IOException e) {
                throw TransferExceptionBuilder.from("Failed to read file data").addParam("path", path).setCause(e).build();
            }
            if (numR <= 0) {
                break;
            }
            // create buffer wrapper for channel NIO API
            ByteBuffer bb = ByteBuffer.wrap(buffer, 0, numR);
            try {
                numW = wbch.write(bb);
            } catch (IOException e) {
                throw TransferExceptionBuilder.from("Failed to write file data").addParam("path", path).setCause(e).build();
            }
            Validate.isTrue(numR == numW);
            // update remaining bytes
            length -= numW;
        }
        Validate.isTrue(length == 0);
    }

    public void finish(long lastModified) {
        Validate.isTrue(isTransfered());

        FileTime lm = FileTime.fromMillis(lastModified);
        try {
            Files.setLastModifiedTime(path, lm);
        } catch (IOException e) {
            throw TransferExceptionBuilder.from("Failed to finalize file").addParam("path", path).setCause(e).build();
        }
    }
}
