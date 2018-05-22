package com.lightcomp.ft.core.send;

import java.io.IOException;
import java.nio.file.Path;

import javax.xml.bind.DatatypeConverter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.common.Checksum;

public class FileChecksumStream implements BlockStream {

    private static final Logger logger = LoggerFactory.getLogger(FileChecksumStream.class);

    private final Checksum checksum;

    private final Path srcPath;

    private byte[] arrChksm;

    private int remaining;

    public FileChecksumStream(Checksum checksum, Path srcPath) {
        this.checksum = checksum;
        this.srcPath = srcPath;
        this.remaining = checksum.getAlgorithm().getByteLen();
    }

    @Override
    public void open() throws IOException {
        arrChksm = checksum.generate();
        if (logger.isDebugEnabled()) {
            logger.debug("File={}, SHA512={}", srcPath, DatatypeConverter.printHexBinary(arrChksm));
        }
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
        len = Math.min(remaining, len);
        // copy checksum to buffer
        int pos = arrChksm.length - remaining;
        System.arraycopy(arrChksm, pos, b, off, len);
        remaining -= len;
        return len;
    }

    @Override
    public void close() throws IOException {
        arrChksm = null;
    }
}
