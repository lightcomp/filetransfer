package com.lightcomp.ft.core.blocks;

import com.lightcomp.ft.core.receiver.ReceiveContext;

public interface FrameBlockProcessor {

    default void receive(ReceiveContext ctx) {
        throw new UnsupportedOperationException();
    }
}
