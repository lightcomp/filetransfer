package com.lightcomp.ft.core.send;

import java.nio.file.Path;

import com.lightcomp.ft.common.Checksum;
import com.lightcomp.ft.core.send.items.SourceFile;

public class FileDataStreamProvider implements BlockStreamProvider {

    private final SourceFile srcFile;

    private final long offset;

    private final long size;

    private final Checksum checksum;

    private final FileDataProgress progress;

    private final Path srcPath;

    public FileDataStreamProvider(SourceFile srcFile, long offset, long size, Checksum checksum,
            FileDataProgress progress, Path srcPath) {
        this.srcFile = srcFile;
        this.offset = offset;
        this.size = size;
        this.checksum = checksum;
        this.progress = progress;
        this.srcPath = srcPath;
    }

    @Override
    public long getStreamSize() {
        return size;
    }

    @Override
    public BlockStream create() {
        return new FileDataStream(srcFile, offset, size, checksum, progress, srcPath);
    }
}
