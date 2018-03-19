package com.lightcomp.ft.sender.impl.phase.frame;

import java.util.ArrayList;
import java.util.List;

import javax.activation.DataHandler;

import org.apache.commons.lang3.Validate;

import com.lightcomp.ft.exception.CanceledException;
import com.lightcomp.ft.sender.impl.TransferContext;
import com.lightcomp.ft.sender.impl.phase.FileProvider;
import com.lightcomp.ft.xsd.v1.FileChunk;
import com.lightcomp.ft.xsd.v1.FileChunks;
import com.lightcomp.ft.xsd.v1.Frame;

public class FrameContext {

    private final List<FrameBlockContext> blockCtxList = new ArrayList<>();

    private final String frameId;

    private final TransferContext transferCtx;

    private long size;

    public FrameContext(String frameId, TransferContext transferCtx) {
        this.frameId = frameId;
        this.transferCtx = transferCtx;
    }

    public String getFrameId() {
        return frameId;
    }

    public long getSize() {
        return size;
    }

    public Frame createFrame() {
        Validate.isTrue(blockCtxList.size() > 0);

        Frame frame = new Frame();
        frame.setFrameId(frameId);
        frame.setSize(size);

        FileChunks fcs = new FileChunks();
        List<FileChunk> fcList = fcs.getList();
        for (FrameBlockContext bc : blockCtxList) {
            fcList.add(bc.createFileChunk());
        }
        frame.setFileChunks(fcs);

        FrameReadableDataSource ds = new FrameReadableDataSource(blockCtxList);
        frame.setData(new DataHandler(ds));

        return frame;
    }

    public long addFile(FileProvider fileProvider, long offset, long length) throws CanceledException {
        Validate.isTrue(offset >= 0);
        Validate.isTrue(length > 0);

        if (transferCtx.isCanceled()) {
            throw new CanceledException();
        }

        long maxBlockSize = transferCtx.getSenderConfig().getMaxFrameSize();
        long blockSize = Math.min(length, maxBlockSize - this.size);

        FrameBlockContext bc = new FrameBlockContext(fileProvider, offset, this.size, blockSize);
        blockCtxList.add(bc);

        // increment frame size
        size += blockSize;

        return blockSize;
    }
}
