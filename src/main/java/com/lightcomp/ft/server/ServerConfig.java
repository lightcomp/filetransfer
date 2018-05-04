package com.lightcomp.ft.server;

import org.apache.commons.lang3.Validate;

public class ServerConfig {

    private final TransferReceiver receiver;
    
    private final TransferStatusStorage statusStorage;    
    
    private int inactiveTimeout = 60 * 5;
    
    public ServerConfig(TransferReceiver receiver, TransferStatusStorage statusStorage) {
        this.receiver = Validate.notNull(receiver);
        this.statusStorage = Validate.notNull(statusStorage);
    }

    public TransferReceiver getReceiver() {
        return receiver;
    }

    public TransferStatusStorage getStatusStorage() {
        return statusStorage;
    }
    
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
