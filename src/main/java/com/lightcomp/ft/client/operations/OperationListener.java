package com.lightcomp.ft.client.operations;

public interface OperationListener {

    boolean isOperationFeasible(Operation operation);

    void onBeginSuccess(String transferId);

    void onLastFrameSuccess();

    void onFinishSuccess();
}
