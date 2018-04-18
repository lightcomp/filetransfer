package com.lightcomp.ft.client.internal.upload.blocks;

import com.lightcomp.ft.client.internal.upload.FrameContext;
import com.lightcomp.ft.xsd.v1.DirBegin;

public class DirBeginBlock implements FrameBlock {

    private final String name;

    public DirBeginBlock(String name) {
        this.name = name;
    }

    @Override
    public boolean addToFrame(FrameContext frameCtx) {
        if (frameCtx.isBlockListFull()) {
            return false;
        }
        DirBegin dir = new DirBegin();
        dir.setName(name);

        frameCtx.addBlock(dir);

        return true;
    }

    @Override
    public FrameBlock getNext() {
        return null;
    }
}
