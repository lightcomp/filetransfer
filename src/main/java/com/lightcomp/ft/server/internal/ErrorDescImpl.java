package com.lightcomp.ft.server.internal;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import com.lightcomp.ft.server.ErrorDesc;

public class ErrorDescImpl implements ErrorDesc {

    private final String message;

    private String detail;

    private Map<String, Object> params;

    private StackTraceElement[] stackTrace;

    public ErrorDescImpl(String message) {
        this.message = message;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    @Override
    public Map<String, Object> getParams() {
        if (params == null) {
            return null;
        }
        return Collections.unmodifiableMap(params);
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    @Override
    public StackTraceElement[] getStackTrace() {
        return stackTrace;
    }

    public void setStackTrace(StackTraceElement[] stackTrace) {
        this.stackTrace = stackTrace;
    }

    @Override
    public String toString() {
        return "ErrorDescImpl [message=" + message + ", detail=" + detail + ", params=" + params + ", stackTrace="
                + Arrays.toString(stackTrace) + "]";
    }
}
