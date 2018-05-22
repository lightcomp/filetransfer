package com.lightcomp.ft.common;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.apache.commons.lang3.Validate;

public class ChecksumGenerator implements Checksum {

    private final Algorithm algorithm;

    private final MessageDigest md;

    // total number of processed bytes
    private long numProcessed;

    private byte[] result;

    private ChecksumGenerator(Algorithm algorithm, MessageDigest md) {
        this.algorithm = algorithm;
        this.md = md;
    }

    @Override
    public Algorithm getAlgorithm() {
        return algorithm;
    }

    /**
     * Total number of bytes processed from data.
     */
    public synchronized long getNumProcessed() {
        return numProcessed;
    }

    @Override
    public synchronized void update(long newPos, byte[] b, int off, int len) {
        // when result generated just check boundaries
        if (result != null) {
            Validate.isTrue(newPos <= numProcessed);
            return;
        }
        // check if more bytes were processed than is current position
        if (newPos > numProcessed) {
            // calculate new offset and length for checksum
            int newLen = (int) (newPos - numProcessed);
            int newOff = off + (len - newLen);
            // update checksum
            md.update(b, newOff, newLen);
            // increment number of bytes processed
            numProcessed += newLen;
        }
    }

    @Override
    public synchronized byte[] generate() {
        if (result == null) {
            result = md.digest();
        }
        return result;
    }

    public static ChecksumGenerator create(Algorithm algorithm) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance(algorithm.getRealName());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        return new ChecksumGenerator(algorithm, md);
    }
}
