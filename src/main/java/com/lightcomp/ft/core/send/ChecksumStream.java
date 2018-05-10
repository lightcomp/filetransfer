package com.lightcomp.ft.core.send;

import java.io.IOException;

import javax.xml.bind.DatatypeConverter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.common.ChecksumGenerator;

class ChecksumStream implements FrameBlockStream {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChecksumStream.class);

    private final ChecksumGenerator chksmGenerator;

    private byte[] arrChksm;

    private int remaining;

    public ChecksumStream(ChecksumGenerator chksmGenerator, byte[] arrChksm) {
        this.chksmGenerator = chksmGenerator;
        this.arrChksm = arrChksm;
    }

    @Override
    public long getSize() {
        return ChecksumGenerator.LENGTH;
    }

    @Override
    public void open() throws IOException {
        if (arrChksm == null) {
            arrChksm = chksmGenerator.generate();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("SHA512={}", DatatypeConverter.printHexBinary(arrChksm));
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
        len = Math.min(remaining, len);
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
