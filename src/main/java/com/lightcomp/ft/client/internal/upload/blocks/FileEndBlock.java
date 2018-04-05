package com.lightcomp.ft.client.internal.upload.blocks;

import com.lightcomp.ft.client.internal.upload.frame.FrameContext;
import com.lightcomp.ft.xsd.v1.FileEnd;

class FileEndBlock implements Block {

    private final FileContext fileCtx;

    public FileEndBlock(FileContext fileCtx) {
        this.fileCtx = fileCtx;
    }

    @Override
    public boolean create(FrameContext frameCtx) {
        if (frameCtx.isBlockListFull()) {
            return false;
        }
        FileEnd fe = new FileEnd();
        fe.setItemId(fileCtx.getItemId());
        fe.setLastModified(fileCtx.getLastModified());
        fe.setChecksum(fileCtx.getChecksum());

        frameCtx.addBlock(fe);

        return true;
    }

    @Override
    public Block getNext() {
        return null;
    }
}
