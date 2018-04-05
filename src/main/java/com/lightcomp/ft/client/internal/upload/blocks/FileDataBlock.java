package com.lightcomp.ft.client.internal.upload.blocks;

import com.lightcomp.ft.client.internal.upload.frame.FrameContext;
import com.lightcomp.ft.xsd.v1.FileData;

class FileDataBlock implements Block {

    private final FileContext fileCtx;

    private final long offset;

    private long size = -1;

    public FileDataBlock(FileContext fileCtx, long offset) {
        this.fileCtx = fileCtx;
        this.offset = offset;
    }

    @Override
    public boolean create(FrameContext frameCtx) {
        if (offset == fileCtx.getSize()) {
            size = 0; // end of file
            return true;
        }
        long remFrameSize = frameCtx.getRemainingSize();
        if (remFrameSize == 0 || frameCtx.isBlockListFull()) {
            return false;
        }
        long remFileSize = fileCtx.getSize() - offset;
        size = Math.min(remFileSize, remFrameSize);

        FileData fd = new FileData();
        fd.setItemId(fileCtx.getItemId());
        fd.setOffset(offset);
        fd.setFrameOffset(frameCtx.getSize());
        fd.setSize(size);

        frameCtx.addBlock(fd);
        frameCtx.addDataProvider(fileCtx.getDataProvider(offset, size));

        return true;
    }

    @Override
    public Block getNext() {
        if (size > 0) {
            return new FileDataBlock(fileCtx, offset + size);
        }
        if (size == 0) {
            return new FileEndBlock(fileCtx);
        }
        throw new IllegalStateException("Invalid file data block size, value=" + size);
    }

}
