package com.lightcomp.ft.client.internal.upload.blocks;

import com.lightcomp.ft.client.SourceFile;
import com.lightcomp.ft.client.internal.upload.frame.FrameContext;
import com.lightcomp.ft.xsd.v1.FileBegin;

public class FileBlock implements Block {

    private final FileContext fileCtx;

    public FileBlock(String itemId, String parentItemId, SourceFile srcFile) {
        this.fileCtx = FileContext.create(itemId, parentItemId, srcFile);
    }

    @Override
    public boolean create(FrameContext frameCtx) {
        if (frameCtx.isBlockListFull()) {
            return false;
        }
        FileBegin fb = new FileBegin();
        fb.setItemId(fileCtx.getItemId());
        fb.setParentItemId(fileCtx.getParentItemId());
        fb.setName(fileCtx.getName());
        fb.setSize(fileCtx.getSize());

        frameCtx.addBlock(fb);

        return true;
    }

    @Override
    public Block getNext() {
        return new FileDataBlock(fileCtx, 0);
    }
}
