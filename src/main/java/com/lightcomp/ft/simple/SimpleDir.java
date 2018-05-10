package com.lightcomp.ft.simple;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

import com.lightcomp.ft.core.send.items.SourceDir;
import com.lightcomp.ft.core.send.items.SourceFile;
import com.lightcomp.ft.core.send.items.SourceItem;

/**
 * File system directory
 *
 */
public class SimpleDir implements SourceDir {

    private final Path dirPath;

    public SimpleDir(Path dirPath) {
        this.dirPath = dirPath;
    }

    @Override
    public String getName() {
        return dirPath.getName(dirPath.getNameCount()-1).toString();
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
            return Files.walk(dirPath,1).filter(p -> !p.equals(dirPath)).<SourceItem>map(p -> {
                if (Files.isDirectory(p)) {
                    return new SimpleDir(p);
                } else {
                    return new SimpleSourceFile(p);
                }
            }).iterator();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
