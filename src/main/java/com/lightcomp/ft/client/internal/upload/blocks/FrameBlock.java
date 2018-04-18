package com.lightcomp.ft.client.internal.upload.blocks;

import com.lightcomp.ft.client.internal.upload.FrameContext;

public interface FrameBlock {

    /**
     * @return Returns true if block was added (specified frame had enough space).
     */
    boolean addToFrame(FrameContext frameCtx);

    FrameBlock getNext();
}
