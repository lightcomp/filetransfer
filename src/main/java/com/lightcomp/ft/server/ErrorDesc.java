package com.lightcomp.ft.server;

import java.util.Map;

/**
 * Error description of transfer failure.
 */
public interface ErrorDesc {

    /**
     * Text description.
     */
    String getMessage();

    /**
     * Optional detail.
     */
    String getDetail();

    /**
     * Optional parameters for more precise description.
     */
    Map<String, Object> getParams();

    /**
     * Optional stack trace.
     */
    StackTraceElement[] getStackTrace();
}
