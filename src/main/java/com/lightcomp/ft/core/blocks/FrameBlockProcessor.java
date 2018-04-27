package com.lightcomp.ft.core.blocks;

import com.lightcomp.ft.core.recv.RecvContext;

public interface FrameBlockProcessor {

    default void receive(RecvContext ctx) {
        throw new UnsupportedOperationException();
    }
}
