package com.lightcomp.ft.server;

import org.apache.commons.lang3.Validate;

public class ReceiverConfig {

    private int inactiveTimeout;

    /**
     * @return Number of seconds until consider inactive.
     */
    public int getInactiveTimeout() {
        return inactiveTimeout;
    }

    /**
     * @param inactiveTimeout
     *            number of seconds until consider inactive, greater than zero
     */
    public void setInactiveTimeout(int inactiveTimeout) {
        Validate.isTrue(inactiveTimeout > 0);
        this.inactiveTimeout = inactiveTimeout;
    }
}
