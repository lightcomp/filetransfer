package com.lightcomp.ft.sender;

import org.apache.commons.lang3.Validate;

/**
 * Sender service configuration.
 */
public final class SenderConfig {

    private final String address;

    private int threadPoolSize = 1;

    private int requestTimeout = 60;

    private int recoveryDelay = 60;

    private long maxFrameSize = 10240;

    /**
     * @param address
     *            receiver service address
     */
    public SenderConfig(String address) {
        this.address = Validate.notEmpty(address);
    }

    /**
     * @return Receiver service address.
     */
    public String getAddress() {
        return address;
    }

    /**
     * @return Number of possible running transfers in parallel.
     */
    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    /**
     * @param threadPoolSize
     *            number of possible running transfers in parallel
     */
    public void setThreadPoolSize(int threadPoolSize) {
        Validate.isTrue(threadPoolSize > 0);
        this.threadPoolSize = threadPoolSize;
    }

    /**
     * @return Receiver request timeout (seconds).
     */
    public int getRequestTimeout() {
        return requestTimeout;
    }

    /**
     * @param requestTimeout
     *            receiver request timeout (seconds)
     */
    public void setRequestTimeout(int requestTimeout) {
        Validate.isTrue(requestTimeout >= 0);
        this.requestTimeout = requestTimeout;
    }

    /**
     * @return Recovery delay (seconds).
     */
    public int getRecoveryDelay() {
        return recoveryDelay;
    }

    /**
     * @param recoveryDelay
     *            recovery delay (seconds)
     */
    public void setRecoveryDelay(int recoveryDelay) {
        Validate.isTrue(recoveryDelay > 0);
        this.recoveryDelay = recoveryDelay;
    }

    /**
     * @return Maximum frame size (kB).
     */
    public long getMaxFrameSize() {
        return maxFrameSize;
    }

    /**
     * @param maxFrameSize
     *            maximum frame size (kB)
     */
    public void setMaxFrameSize(long maxFrameSize) {
        Validate.isTrue(maxFrameSize > 0);
        this.maxFrameSize = maxFrameSize;
    }
}
