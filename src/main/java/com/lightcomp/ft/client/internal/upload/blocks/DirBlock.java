package com.lightcomp.ft.client.internal.upload.blocks;

import com.lightcomp.ft.client.SourceDir;
import com.lightcomp.ft.client.internal.upload.frame.FrameContext;
import com.lightcomp.ft.xsd.v1.Dir;

public class DirBlock implements Block {

    private final String itemId;

    private final String parentItemId;

    private final String name;

    public DirBlock(String itemId, String parentItemId, SourceDir srcDir) {
        this.itemId = itemId;
        this.parentItemId = parentItemId;
        this.name = srcDir.getName();
    }

    @Override
    public boolean create(FrameContext frameCtx) {
        if (frameCtx.isBlockListFull()) {
            return false;
        }
        Dir dir = new Dir();
        dir.setItemId(itemId);
        dir.setParentItemId(parentItemId);
        dir.setName(name);

        frameCtx.addBlock(dir);

        return true;
    }

    @Override
    public Block getNext() {
        return null;
    }
}
