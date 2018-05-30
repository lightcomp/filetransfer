package com.lightcomp.ft.client.internal.operations;

public class BeginResult extends OperationResult {

    private final String transferId;

    public BeginResult(Type type, String transferId) {
        super(type);
        this.transferId = transferId;
    }

    public BeginResult(Type type, OperationError error) {
        super(type, error);
        this.transferId = null;
    }

    public String getTransferId() {
        return transferId;
    }
}
