package com.lightcomp.ft.client.internal.operations;

public class OperationResult {

    public enum Type {
        SUCCESS, FAIL, CANCEL
    }

    private final Type type;

    private final OperationError error;

    public OperationResult(Type type, OperationError error) {
        this.type = type;
        this.error = error;
    }

    public OperationResult(Type type) {
        this(type, null);
    }

    public Type getType() {
        return type;
    }

    public OperationError getError() {
        return error;
    }
}
