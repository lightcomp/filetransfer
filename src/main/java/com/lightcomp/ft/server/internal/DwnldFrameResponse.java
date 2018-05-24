package com.lightcomp.ft.server.internal;

import com.lightcomp.ft.core.send.SendFrameContext;
import com.lightcomp.ft.server.TransferStatus;
import com.lightcomp.ft.xsd.v1.Frame;

public class DwnldFrameResponse {

    private final ErrorContext error;

    private final SendFrameContext frameCtx;

    private final TransferStatus status;

    public DwnldFrameResponse(ErrorContext error) {
        this.error = error;
        this.frameCtx = null;
        this.status = null;
    }

    public DwnldFrameResponse(SendFrameContext frameCtx) {
        this.frameCtx = frameCtx;
        this.error = null;
        this.status = null;
    }

    public DwnldFrameResponse(SendFrameContext frameCtx, TransferStatus status) {
        this.frameCtx = frameCtx;
        this.status = status;
        this.error = null;
    }

    public boolean isFrameChanged() {
        return status != null;
    }

    public ErrorContext getError() {
        return error;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public Frame getFrame() {
        return frameCtx.createFrame();
    }
}
