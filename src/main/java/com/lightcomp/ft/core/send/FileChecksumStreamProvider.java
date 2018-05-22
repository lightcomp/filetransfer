package com.lightcomp.ft.core.send;

import java.nio.file.Path;

import com.lightcomp.ft.common.Checksum;

public class FileChecksumStreamProvider implements BlockStreamProvider {

    private final Checksum checksum;

    private final Path srcPath;

    public FileChecksumStreamProvider(Checksum checksum, Path srcPath) {
        this.checksum = checksum;
        this.srcPath = srcPath;
    }

    @Override
    public long getStreamSize() {
        return checksum.getAlgorithm().getByteLen();
    }

    @Override
    public BlockStream create() {
        return new FileChecksumStream(checksum, srcPath);
    }
}
