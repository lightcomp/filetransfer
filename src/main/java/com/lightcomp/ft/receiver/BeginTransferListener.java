package com.lightcomp.ft.receiver;

public interface BeginTransferListener {

    TransferAcceptor onTransferBegin(String requestId);
}
