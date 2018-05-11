package com.lightcomp.ft.core.send.items;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;

public class SimpleFile implements SourceFile {

    private final Path path;

    public SimpleFile(Path path) {
        this.path = path;
    }

    @Override
    public String getName() {
        return path.getFileName().toString();
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
        try {
            return Files.size(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public long getLastModified() {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public byte[] getChecksum() {
        return null;
    }

    @Override
    public ReadableByteChannel openChannel(long position) throws IOException {
    	SeekableByteChannel channel = Files.newByteChannel(path);
    	channel.position(position);
    	return channel;
    }
}