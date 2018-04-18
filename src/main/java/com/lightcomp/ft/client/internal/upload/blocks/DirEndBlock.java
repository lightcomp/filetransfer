package com.lightcomp.ft.client.internal.upload.blocks;

import com.lightcomp.ft.client.internal.upload.FrameContext;
import com.lightcomp.ft.xsd.v1.DirEnd;

public class DirEndBlock implements FrameBlock {

    @Override
    public boolean addToFrame(FrameContext frameCtx) {
        if (frameCtx.isBlockListFull()) {
            return false;
        }
        frameCtx.addBlock(new DirEnd());

        return true;
    }

    @Override
    public FrameBlock getNext() {
        return null;
    }
}
