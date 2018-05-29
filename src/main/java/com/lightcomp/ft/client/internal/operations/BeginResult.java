package com.lightcomp.ft.client.internal.operations;

public class BeginResult extends OperationResult {

    private final String transferId;

    public BeginResult(Type type, String transferId) {
        super(type);
        this.transferId = transferId;
    }

    public BeginResult(Type type, ErrorDesc errorDesc) {
        super(type, errorDesc);
        this.transferId = null;
    }

    public String getTransferId() {
        return transferId;
    }
}
