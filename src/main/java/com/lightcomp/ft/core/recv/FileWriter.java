package com.lightcomp.ft.core.recv;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;

import javax.xml.bind.DatatypeConverter;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.common.Checksum.Algorithm;
import com.lightcomp.ft.common.ChecksumByteChannel;
import com.lightcomp.ft.common.ChecksumGenerator;
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.exception.TransferExceptionBuilder;

class FileWriter {

    private static final Logger logger = LoggerFactory.getLogger(FileWriter.class);

    private static final int BUFFER_SIZE = 65536;

    private final Path file;

    private final long size;

    private final ChecksumGenerator chksmGenerator;

    public FileWriter(Path file, long size, Algorithm checksumAlg) {
        this.file = file;
        this.size = size;
        this.chksmGenerator = ChecksumGenerator.create(checksumAlg);
    }

    public Path getFile() {
        return file;
    }

    public void write(ReadableByteChannel rbch, long offset, long length) throws TransferException {
        long writtenSize = chksmGenerator.getNumProcessed();
        if (offset != writtenSize) {
            throw new TransferExceptionBuilder("File must be written in sequence").addParam("path", file)
                    .addParam("writtenSize", writtenSize).addParam("fileOffset", offset).build();
        }
        long newSize = offset + length;
        if (newSize > size) {
            throw new TransferExceptionBuilder("File data overlaps defined size").addParam("path", file)
                    .addParam("fileSize", size).addParam("newSize", newSize).build();
        }
        if (length == 0) {
            return;
        }
        // open file for write at the end
        try (SeekableByteChannel sbch = Files.newByteChannel(file, StandardOpenOption.APPEND)) {
            Validate.isTrue(sbch.position() == offset);
            // wrap channel for checksum calculating
            try (WritableByteChannel wbch = new ChecksumByteChannel(sbch, chksmGenerator, offset)) {
                copyData(rbch, wbch, length);
            }
        } catch (IOException e) {
            throw new TransferExceptionBuilder("Failed to open file").addParam("path", file).setCause(e).build();
        }
    }

    public void finish(long lastModified, byte[] checksum) throws TransferException {
        // check written size
        long writtenSize = chksmGenerator.getNumProcessed();
        if (size != writtenSize) {
            throw new TransferExceptionBuilder("Incomplete file cannot be finished").addParam("path", file)
                    .addParam("fileSize", size).addParam("writtenSize", writtenSize).build();
        }
        // validate checksum
        byte[] genChecksum = chksmGenerator.generate();
        if (logger.isDebugEnabled()) {
            logger.debug("File={}, SHA512={}", file, DatatypeConverter.printHexBinary(genChecksum));
        }
        if (!Arrays.equals(genChecksum, checksum)) {
            throw new TransferExceptionBuilder("File checksums does not match").addParam("path", file).build();
        }
        // update last modification
        FileTime lm = FileTime.fromMillis(lastModified);
        try {
            Files.setLastModifiedTime(file, lm);
        } catch (IOException e) {
            throw new TransferExceptionBuilder("Failed to finilsh file").addParam("path", file).setCause(e).build();
        }
    }

    private void copyData(ReadableByteChannel rbch, WritableByteChannel wbch, long length) throws TransferException {
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
                throw new TransferExceptionBuilder("Failed to read file data").addParam("path", file).setCause(t)
                        .build();
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
                throw new TransferExceptionBuilder("Failed to write file data").addParam("path", file).setCause(t)
                        .build();
            }
            length -= bb.limit();
            bb.rewind();
        }
    }
}
