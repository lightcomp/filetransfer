package com.lightcomp.ft.client.internal.operations;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.lightcomp.ft.client.internal.ExceptionType;

public class ErrorDesc {

    private final Map<String, Object> params = new LinkedHashMap<>();

    private final String message;

    private Throwable cause;

    private ExceptionType causeType;

    public ErrorDesc(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getParams() {
        return Collections.unmodifiableMap(params);
    }

    public Throwable getCause() {
        return cause;
    }

    public ExceptionType getCauseType() {
        return causeType;
    }

    /* package visible builder methods */

    ErrorDesc addParam(String name, Object value) {
        params.put(name, value);
        return this;
    }

    ErrorDesc setCause(Throwable cause) {
        this.cause = cause;
        return this;
    }

    ErrorDesc setCauseType(ExceptionType causeType) {
        this.causeType = causeType;
        return this;
    }
}