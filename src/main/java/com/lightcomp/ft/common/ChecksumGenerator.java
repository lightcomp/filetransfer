package com.lightcomp.ft.common;

import java.security.MessageDigest;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.Validate;

public class ChecksumGenerator {

    private final MessageDigest md;

    // total number of processed bytes
    private long numProcessed;

    // current data stream position
    private long currStreamPos;

    private byte[] result;

    public ChecksumGenerator(ChecksumType type) {
        this.md = type.createMessageDigest();
    }

    public long getNumProcessed() {
        return numProcessed;
    }

    public void setStreamPos(long streamPos) {
        Validate.isTrue(streamPos >= 0 && streamPos <= numProcessed);
        currStreamPos = streamPos;
    }

    /**
     * @param input
     *            the array of bytes
     * @param offset
     *            the offset to start from in the array of bytes
     * @param length
     *            the number of bytes to use, starting at offset
     */
    public void update(byte[] input, int offset, int length) {
        long posAfterUpdate = currStreamPos + length;
        if (posAfterUpdate <= numProcessed) {
            currStreamPos = posAfterUpdate;
            return;
        }
        // data size cannot be bigger than length
        int newDataSize = (int) (posAfterUpdate - numProcessed);
        int off = offset + (length - newDataSize);

        md.update(input, off, newDataSize);

        numProcessed += newDataSize;
        currStreamPos = posAfterUpdate;
    }

    public String getHexString() {
        if (result == null) {
            result = md.digest();
        }
        return Hex.encodeHexString(result);
    }
}
