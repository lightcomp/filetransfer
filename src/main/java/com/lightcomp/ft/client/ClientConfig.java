package com.lightcomp.ft.client;

import org.apache.commons.lang3.Validate;

/**
 * Sender service configuration.
 */
public final class ClientConfig {

    private final String address;

    private int threadPoolSize = 1;

    private int requestTimeout = 60;

    private int recoveryDelay = 60;

    private long maxFrameSize = 10485760;

    private int maxFrameBlocks = 10000;

    private boolean soapLogging;

    /**
     * @param address
     *            receiver address
     */
    public ClientConfig(String address) {
        this.address = Validate.notEmpty(address);
    }

    /**
     * @return Receiver address.
     */
    public String getAddress() {
        return address;
    }

    /**
     * @return Maximum of simultaneously running transfers.
     */
    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    /**
     * @param threadPoolSize
     *            maximum of simultaneously running transfers, greater than zero
     */
    public void setThreadPoolSize(int threadPoolSize) {
        Validate.isTrue(threadPoolSize > 0);
        this.threadPoolSize = threadPoolSize;
    }

    /**
     * @return Receiver request timeout in seconds.
     */
    public int getRequestTimeout() {
        return requestTimeout;
    }

    /**
     * @param requestTimeout
     *            receiver request timeout in seconds, not negative
     */
    public void setRequestTimeout(int requestTimeout) {
        Validate.isTrue(requestTimeout >= 0);
        this.requestTimeout = requestTimeout;
    }

    /**
     * @return Delay before transfer recovery in seconds.
     */
    public int getRecoveryDelay() {
        return recoveryDelay;
    }

    /**
     * @param recoveryDelay
     *            delay before transfer recovery in seconds, greater than zero
     */
    public void setRecoveryDelay(int recoveryDelay) {
        Validate.isTrue(recoveryDelay > 0);
        this.recoveryDelay = recoveryDelay;
    }

    /**
     * @return Maximum frame size in bytes.
     */
    public long getMaxFrameSize() {
        return maxFrameSize;
    }

    /**
     * @param maxFrameSize
     *            maximum frame size in bytes, greater than zero
     */
    public void setMaxFrameSize(long maxFrameSize) {
        Validate.isTrue(maxFrameSize > 0);
        this.maxFrameSize = maxFrameSize;
    }

    /**
     * @return Maximum number of blocks for one frame.
     */
    public int getMaxFrameBlocks() {
        return maxFrameBlocks;
    }

    /**
     * @param maxFrameBlocks
     *            maximum number of blocks for one frame, greater than zero
     */
    public void setMaxFrameBlocks(int maxFrameBlocks) {
        Validate.isTrue(maxFrameBlocks > 0);
        this.maxFrameBlocks = maxFrameBlocks;
    }

    /**
     * @return Returns true if soap messages are logged.
     */
    public boolean isSoapLogging() {
        return soapLogging;
    }

    /**
     * @param soapLogging
     *            if true soap messages will be logged
     */
    public void setSoapLogging(boolean soapLogging) {
        this.soapLogging = soapLogging;
    }
}
