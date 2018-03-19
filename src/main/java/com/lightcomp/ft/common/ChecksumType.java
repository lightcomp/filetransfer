package com.lightcomp.ft.common;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Supported checksum types.
 */
public enum ChecksumType {
    SHA_256("SHA-256"), SHA_384("SHA-384"), SHA_512("SHA-512");

    private final String value;

    private ChecksumType(String value) {
        this.value = value;
    }

    /**
     * @return Algorithm name defined in FIPS PUB 180-4.
     */
    public String value() {
        return value;
    }

    public MessageDigest createMessageDigest() {
        try {
            return MessageDigest.getInstance(value);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param value
     *            algorithm name defined in FIPS PUB 180-4.
     */
    public static ChecksumType fromValue(String value) {
        for (ChecksumType ct : ChecksumType.values()) {
            if (ct.value.equals(value)) {
                return ct;
            }
        }
        throw new IllegalArgumentException("Unsupported checksum type, value=" + value);
    }
}