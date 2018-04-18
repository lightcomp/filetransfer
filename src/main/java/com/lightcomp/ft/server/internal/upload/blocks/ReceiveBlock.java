package com.lightcomp.ft.server.internal.upload.blocks;

import java.io.InputStream;

import com.lightcomp.ft.server.internal.upload.FrameContext;

public interface ReceiveBlock {

    default void saveBlock(InputStream is, FrameContext frameCtx) {
        throw new UnsupportedOperationException();
    }
}
