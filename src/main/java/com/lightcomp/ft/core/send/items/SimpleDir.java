package com.lightcomp.ft.core.send.items;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

/**
 * File system directory
 */
public class SimpleDir implements SourceDir {

    private final Path path;

    public SimpleDir(Path path) {
        this.path = path;
    }

    @Override
    public String getName() {
        return path.getFileName().toString();
    }

    @Override
    public boolean isDir() {
        return true;
    }

    @Override
    public SourceDir asDir() {
        return this;
    }

    @Override
    public SourceFile asFile() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<SourceItem> getItemIterator() {
        try {
            return Files.list(path).<SourceItem>map(p -> {
                if (Files.isDirectory(p)) {
                    return new SimpleDir(p);
                } else {
                    return new SimpleFile(p);
                }
            }).iterator();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
