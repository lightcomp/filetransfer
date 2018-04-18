package com.lightcomp.ft.exception;

import com.lightcomp.ft.core.TransferInfo;

public class CanceledException extends Exception {

    private static final long serialVersionUID = 1L;

    public static void checkTransfer(TransferInfo transferInfo) throws CanceledException {
        if (transferInfo.isCancelRequested()) {
            throw new CanceledException();
        }
    }
}
