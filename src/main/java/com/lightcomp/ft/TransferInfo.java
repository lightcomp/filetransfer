package com.lightcomp.ft;

public interface TransferInfo {

    String getTransferId();

    String getRequestId();

    boolean isCancelRequested();
}
