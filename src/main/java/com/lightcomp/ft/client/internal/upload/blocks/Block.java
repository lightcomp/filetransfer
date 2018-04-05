package com.lightcomp.ft.client.internal.upload.blocks;

import com.lightcomp.ft.client.internal.upload.frame.FrameContext;

public interface Block {

    boolean create(FrameContext frameCtx);

    Block getNext();
}
