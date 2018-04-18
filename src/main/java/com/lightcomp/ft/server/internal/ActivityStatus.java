package com.lightcomp.ft.server.internal;

public enum ActivityStatus {
    /**
     * Transfer is active (can be active even after termination).
     */
    ACTIVE,
    /**
     * Transfer is inactive but not terminated.
     */
    INACTIVE_RUNNING,
    /**
     * Transfer is inactive and terminated.
     */
    INACTIVE_TERMINATED
}