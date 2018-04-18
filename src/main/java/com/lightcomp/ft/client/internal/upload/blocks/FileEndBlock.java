package com.lightcomp.ft.client.internal.upload.blocks;

import com.lightcomp.ft.client.internal.upload.FileContext;
import com.lightcomp.ft.client.internal.upload.FrameContext;
import com.lightcomp.ft.xsd.v1.FileEnd;

class FileEndBlock implements FrameBlock {

    private final FileContext fileCtx;

    public FileEndBlock(FileContext fileCtx) {
        this.fileCtx = fileCtx;
    }

    @Override
    public boolean addToFrame(FrameContext frameCtx) {
        if (frameCtx.isBlockListFull()) {
            return false;
        }
        FileEnd fe = new FileEnd();
        fe.setLastModified(fileCtx.getLastModified());
        fe.setChecksum(fileCtx.getChecksum());

        frameCtx.addBlock(fe);

        return true;
    }

    @Override
    public FrameBlock getNext() {
        return null;
    }
}
