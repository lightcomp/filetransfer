package com.lightcomp.ft.common;

public interface Checksum {

    public enum Algorithm {
        SHA_512(64);

        private final int byteLen;

        private Algorithm(int byteLen) {
            this.byteLen = byteLen;
        }

        public String getRealName() {
            return name().replace('_', '-');
        }

        public int getByteLen() {
            return byteLen;
        }
    }

    /**
     * Hash algorithm used for current instance.
     */
    Algorithm getAlgorithm();
    
    /**
     * @param newPos
     *            the new position in data - length of the byte array included
     * @param b
     *            the byte array to update the checksum with
     * @param off
     *            the start offset of the byte array
     * @param len
     *            the number of bytes to use for the update
     */
    void update(long newPos, byte[] b, int off, int len);

    /**
     * Generates checksum from current value. After call checksum is no more updatable.
     */
    byte[] generate();
}
