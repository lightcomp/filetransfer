package com.lightcomp.ft;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.TimeUnit;

import com.lightcomp.ft.sender.SourceDir;
import com.lightcomp.ft.sender.SourceFile;

public class SourceFileImpl implements SourceFile {

    @Override
    public String getName() {
        return "testFile.txt";
    }

    @Override
    public boolean isDir() {
        return false;
    }

    @Override
    public SourceDir asDir() {
        throw new UnsupportedOperationException();
    }

    @Override
    public SourceFile asFile() {
        return this;
    }

    @Override
    public long getSize() {
        return 0;
    }

    @Override
    public FileTime getLastModified() {
        return FileTime.from(1, TimeUnit.MILLISECONDS);
    }

    @Override
    public String getChecksum() {
        return null;
    }

    @Override
    public SeekableByteChannel openChannel() throws IOException {
        return null;
    }

}
