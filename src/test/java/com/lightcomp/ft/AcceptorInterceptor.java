package com.lightcomp.ft;

import com.lightcomp.ft.server.TransferStatus;

public interface AcceptorInterceptor {

    void onTransferProgress(TransferStatus status);

    void onTransferSuccess();

    void onTransferCanceled();

    void onTransferFailed(Throwable cause);
}