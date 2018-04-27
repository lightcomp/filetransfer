package com.lightcomp.ft.core.recv;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;

import org.apache.commons.lang3.Validate;

import com.lightcomp.ft.common.ChecksumByteChannel;
import com.lightcomp.ft.common.ChecksumGenerator;
import com.lightcomp.ft.exception.TransferExceptionBuilder;

class FileWriter {

    private static final int BUFFER_SIZE = 65536;

    private final Path path;

    private final long size;

    private final ChecksumGenerator chksmGenerator;

    public FileWriter(Path path, long size) {
        this.path = path;
        this.size = size;
        this.chksmGenerator = ChecksumGenerator.create();
    }

    public Path getPath() {
        return path;
    }

    public void write(ReadableByteChannel rbch, long offset, long length) {
        long writtenSize = chksmGenerator.getNumProcessed();
        if (offset != writtenSize) {
            throw TransferExceptionBuilder.from("File must be written in sequence").addParam("path", path)
                    .addParam("writtenSize", writtenSize).addParam("fileOffset", offset).build();
        }
        long newSize = offset + length;
        if (newSize > size) {
            throw TransferExceptionBuilder.from("File data overlaps defined size").addParam("path", path)
                    .addParam("fileSize", size).addParam("newSize", newSize).build();
        }
        if (length == 0) {
            return;
        }
        // prepare open options
        OpenOption[] openOptions = new OpenOption[] { StandardOpenOption.WRITE,
                writtenSize > 0 ? StandardOpenOption.APPEND : StandardOpenOption.CREATE_NEW };
        // open file for write at the end
        try (SeekableByteChannel sbch = Files.newByteChannel(path, openOptions)) {
            Validate.isTrue(sbch.position() == offset);
            // wrap channel for checksum calculating
            try (WritableByteChannel wbch = new ChecksumByteChannel(sbch, chksmGenerator)) {
                copyData(rbch, wbch, length);
            }
        } catch (IOException e) {
            throw TransferExceptionBuilder.from("Failed to open file").addParam("path", path).setCause(e).build();
        }
    }

    public void finish(long lastModified, byte[] checksum) {
        long writtenSize = chksmGenerator.getNumProcessed();
        if (size != writtenSize) {
            throw TransferExceptionBuilder.from("Incomplete file cannot be finished").addParam("path", path)
                    .addParam("fileSize", size).addParam("writtenSize", writtenSize).build();
        }
        byte[] chksm = chksmGenerator.generate();
        if (!Arrays.equals(chksm, checksum)) {
            throw TransferExceptionBuilder.from("File checksums does not match").addParam("path", path)
                    .addParam("expectedChecksum", chksm).addParam("receivedChecksum", checksum).build();
        }
        FileTime lm = FileTime.fromMillis(lastModified);
        try {
            Files.setLastModifiedTime(path, lm);
        } catch (IOException e) {
            throw TransferExceptionBuilder.from("Failed to finilsh file").addParam("path", path).setCause(e).build();
        }
    }

    private void copyData(ReadableByteChannel rbch, WritableByteChannel wbch, long length) {
        ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);

        while (length > 0) {
            // set buffer limit to remaining length
            if (length < BUFFER_SIZE) {
                bb.limit((int) length);
            }
            try {
                int n = rbch.read(bb);
                if (n <= 0) {
                    // buffer limit is handled by while condition
                    Validate.isTrue(n < 0);
                    // stop copy when EOF
                    break;
                }
            } catch (Throwable t) {
                throw TransferExceptionBuilder.from("Failed to read file data").addParam("path", path).setCause(t).build();
            }
            // flip buffer for write
            bb.flip();
            try {
                // try to write whole buffer
                while (wbch.write(bb) > 0) {
                }
                // destination file must be large enough
                Validate.isTrue(!bb.hasRemaining());
            } catch (Throwable t) {
                throw TransferExceptionBuilder.from("Failed to write file data").addParam("path", path).setCause(t).build();
            }
            length -= bb.limit();
            bb.rewind();
        }
    }
}
