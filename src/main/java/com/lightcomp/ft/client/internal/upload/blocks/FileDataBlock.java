package com.lightcomp.ft.client.internal.upload.blocks;

import com.lightcomp.ft.client.internal.upload.FileContext;
import com.lightcomp.ft.client.internal.upload.FrameBlockData;
import com.lightcomp.ft.client.internal.upload.FrameContext;
import com.lightcomp.ft.xsd.v1.FileData;

class FileDataBlock implements FrameBlock {

    private final FileContext fileCtx;

    private final long offset;

    private long size = -1;

    public FileDataBlock(FileContext fileCtx, long offset) {
        this.fileCtx = fileCtx;
        this.offset = offset;
    }

    @Override
    public boolean addToFrame(FrameContext frameCtx) {
        if (offset == fileCtx.getSize()) {
            size = 0; // end of file
            return true;
        }
        long remDataSize = frameCtx.getRemainingDataSize();
        if (remDataSize == 0 || frameCtx.isBlockListFull()) {
            return false;
        }
        long remFileSize = fileCtx.getSize() - offset;
        size = Math.min(remFileSize, remDataSize);

        FileData fd = new FileData();
        fd.setOffset(offset);
        fd.setFrameOffset(frameCtx.getDataSize());
        fd.setSize(size);

        FrameBlockData fbd = fileCtx.createFrameBlockData(offset, size);
        frameCtx.addDataBlock(fd, fbd);

        return true;
    }

    @Override
    public FrameBlock getNext() {
        if (size > 0) {
            return new FileDataBlock(fileCtx, offset + size);
        }
        if (size == 0) {
            return new FileEndBlock(fileCtx);
        }
        throw new IllegalStateException("Invalid file data block size, value=" + size);
    }

}
