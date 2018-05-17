package com.lightcomp.ft.client.operations;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.lightcomp.ft.client.internal.ExceptionType;

public class OperationStatus {

    public enum Type {
        SUCCESS, FAIL, CANCEL
    }

    private final Map<String, Object> failureParams = new LinkedHashMap<>();

    private final Type type;

    private String failureMessage;

    private Throwable failureCause;

    private ExceptionType failureType;

    public OperationStatus(Type type) {
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public Map<String, Object> getFailureParams() {
        return Collections.unmodifiableMap(failureParams);
    }

    public Throwable getFailureCause() {
        return failureCause;
    }

    public ExceptionType getFailureType() {
        return failureType;
    }

    /* package visible build methods */

    OperationStatus setFailureMessage(String failureMessage) {
        this.failureMessage = failureMessage;
        return this;
    }

    OperationStatus addFailureParam(String name, Object value) {
        failureParams.put(name, value);
        return this;
    }

    OperationStatus setFailureCause(Throwable failureCause) {
        this.failureCause = failureCause;
        return this;
    }

    OperationStatus setFailureType(ExceptionType failureType) {
        this.failureType = failureType;
        return this;
    }
}
