package com.lightcomp.ft.client.internal.operations;

public class OperationResult {

    public enum Type {
        SUCCESS, FAIL, CANCEL
    }

    private final Type type;

    private final ErrorDesc errorDesc;

    public OperationResult(Type type, ErrorDesc errorDesc) {
        this.type = type;
        this.errorDesc = errorDesc;
    }

    public OperationResult(Type type) {
        this(type, null);
    }

    public Type getType() {
        return type;
    }

    public ErrorDesc getErrorDesc() {
        return errorDesc;
    }
}
