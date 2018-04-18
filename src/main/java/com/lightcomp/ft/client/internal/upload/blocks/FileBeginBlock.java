package com.lightcomp.ft.client.internal.upload.blocks;

import com.lightcomp.ft.client.internal.upload.FileContext;
import com.lightcomp.ft.client.internal.upload.FrameContext;
import com.lightcomp.ft.xsd.v1.FileBegin;

public class FileBeginBlock implements FrameBlock {

    private final FileContext fileCtx;

    public FileBeginBlock(FileContext fileCtx) {
        this.fileCtx = fileCtx;
    }

    @Override
    public boolean addToFrame(FrameContext frameCtx) {
        if (frameCtx.isBlockListFull()) {
            return false;
        }
        FileBegin fb = new FileBegin();
        fb.setName(fileCtx.getName());
        fb.setSize(fileCtx.getSize());

        frameCtx.addBlock(fb);

        return true;
    }

    @Override
    public FrameBlock getNext() {
        return new FileDataBlock(fileCtx, 0);
    }
}
