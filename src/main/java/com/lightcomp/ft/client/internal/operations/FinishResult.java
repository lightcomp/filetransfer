package com.lightcomp.ft.client.internal.operations;

import com.lightcomp.ft.xsd.v1.GenericDataType;

public class FinishResult extends OperationResult {

    private final GenericDataType data;

    public FinishResult(Type type, GenericDataType data) {
        super(type);
        this.data = data;
    }

    public FinishResult(Type type, OperationError error) {
        super(type, error);
        this.data = null;
    }

    public GenericDataType getData() {
        return data;
    }
}
