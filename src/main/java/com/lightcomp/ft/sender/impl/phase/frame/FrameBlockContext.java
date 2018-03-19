package com.lightcomp.ft.sender.impl.phase.frame;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;

import com.lightcomp.ft.sender.impl.phase.FileProvider;
import com.lightcomp.ft.xsd.v1.FileChunk;

public class FrameBlockContext {

    private final FileProvider fileProvider;

    private final long fileOffset;

    private final long offset;

    private final long size;

    public FrameBlockContext(FileProvider fileProvider, long fileOffset, long offset, long size) {
        this.fileProvider = fileProvider;
        this.fileOffset = fileOffset;
        this.offset = offset;
        this.size = size;
    }

    public ReadableByteChannel openChannel() throws IOException {
        ReadableByteChannel rbch = fileProvider.openChannel(fileOffset);
        rbch = new ReadLimitingByteChannel(rbch, size);
        return rbch;
    }

    public FileChunk createFileChunk() {
        FileChunk fc = new FileChunk();
        fc.setFileId(fileProvider.getFileId());
        fc.setFrameOffset(offset);
        fc.setOffset(fileOffset);
        fc.setSize(size);
        return fc;
    }
}