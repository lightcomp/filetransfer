package com.lightcomp.ft.common;

import java.security.MessageDigest;

import org.apache.commons.codec.binary.Hex;

public class ChecksumGenerator {

    private final MessageDigest md;

    private long byteSize;

    public ChecksumGenerator(ChecksumType type) {
        this.md = type.createMessageDigest();
    }

    public long getByteSize() {
        return byteSize;
    }

    public void update(byte[] input, int offset, int len) {
        md.update(input, offset, len);
        byteSize += len;
    }

    public String generate() {
        byte[] b = md.digest();
        return Hex.encodeHexString(b);
    }
}
