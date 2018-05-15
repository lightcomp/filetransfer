package com.lightcomp.ft.core.send;

import java.io.IOException;
import java.nio.file.Path;

import javax.xml.bind.DatatypeConverter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.common.ChecksumGenerator;

class FileChksmStream implements FrameBlockStream {

    private static final Logger logger = LoggerFactory.getLogger(FileChksmStream.class);

    private final ChecksumGenerator chksmGenerator;

    private final Path logPath;

    private byte[] arrChksm;

    private int remaining;

    public FileChksmStream(ChecksumGenerator chksmGenerator, byte[] arrChksm, Path logPath) {
        this.chksmGenerator = chksmGenerator;
        this.arrChksm = arrChksm;
        this.logPath = logPath;
    }

    @Override
    public long getSize() {
        return ChecksumGenerator.LENGTH;
    }

    @Override
    public void open() throws IOException {
        if (arrChksm == null) {
            arrChksm = chksmGenerator.generate();
            if (logger.isDebugEnabled()) {
                logger.debug("File={}, SHA512={}", logPath, DatatypeConverter.printHexBinary(arrChksm));
            }
        }
        remaining = arrChksm.length;
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
        remaining = 0;
    }
}
