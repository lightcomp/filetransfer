package com.lightcomp.ft.server.internal;

import com.lightcomp.ft.core.send.SendFrameContext;
import com.lightcomp.ft.server.TransferStatus;

public class DwnldFrameResult {

    private final ErrorContext error;

    private final SendFrameContext frameCtx;

    private final TransferStatus status;

    public DwnldFrameResult(ErrorContext error) {
        this.error = error;
        this.frameCtx = null;
        this.status = null;
    }

    public DwnldFrameResult(SendFrameContext frameCtx) {
        this.frameCtx = frameCtx;
        this.error = null;
        this.status = null;
    }

    public DwnldFrameResult(SendFrameContext frameCtx, TransferStatus status) {
        this.frameCtx = frameCtx;
        this.status = status;
        this.error = null;
    }

    public boolean isStatusChanged() {
        return status != null;
    }

    public ErrorContext getError() {
        return error;
    }

    public SendFrameContext getFrameCtx() {
        return frameCtx;
    }

    public TransferStatus getStatus() {
        return status;
    }
}
