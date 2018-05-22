package com.lightcomp.ft.common;

import org.apache.commons.lang3.Validate;

public class ChecksumHolder implements Checksum {

    private final Algorithm algorithm;

    private final byte[] checksum;

    public ChecksumHolder(Algorithm algorithm, byte[] checksum) {
        Validate.isTrue(algorithm.getByteLen() == checksum.length);
        this.algorithm = algorithm;
        this.checksum = checksum;
    }

    @Override
    public Algorithm getAlgorithm() {
        return algorithm;
    }

    @Override
    public void update(long pos, byte[] b, int off, int len) {
        // NOP
    }

    @Override
    public byte[] generate() {
        return checksum;
    }
}
