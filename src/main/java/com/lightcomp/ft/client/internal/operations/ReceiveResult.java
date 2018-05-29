package com.lightcomp.ft.client.internal.operations;

import com.lightcomp.ft.xsd.v1.Frame;

public class ReceiveResult extends OperationResult {

    private final Frame frame;

    public ReceiveResult(Type type, Frame frame) {
        super(type);
        this.frame = frame;
    }

    public ReceiveResult(Type type, ErrorDesc errorDesc) {
        super(type, errorDesc);
        this.frame = null;
    }

    public Frame getFrame() {
        return frame;
    }
}