package com.lightcomp.ft.common;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.apache.commons.lang3.Validate;

public class ChecksumGenerator {

    public static final String ALGORITHM = "SHA-512";

    public static final byte LENGTH = 64;

    private final MessageDigest md;

    // total number of processed bytes
    private long numProcessed;

    // current data stream position
    private long currStreamPos;

    private ChecksumGenerator(MessageDigest md) {
        this.md = md;
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
        long posUpdate = currStreamPos + length;
        // check if more bytes were processed than is current position
        if (posUpdate > numProcessed) {
            // calculate new offset and length for checksum
            int newLen = (int) (posUpdate - numProcessed);
            int newOff = offset + (length - newLen);
            // update checksum
            md.update(input, newOff, newLen);
            // increment number of bytes processed
            numProcessed += newLen;
        }
        currStreamPos = posUpdate;
    }

    /**
     * Generates checksum. The generator is reset after this call is made.
     */
    public byte[] generate() {
        byte[] arr = md.digest();
        numProcessed = 0;
        currStreamPos = 0;
        return arr;
    }

    /**
     * Creates checksum generator with hash algorithm SHA-512.
     */
    public static ChecksumGenerator create() {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        return new ChecksumGenerator(md);
    }
}
