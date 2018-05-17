package com.lightcomp.ft.core.send;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Iterator;

import com.lightcomp.ft.common.PathUtils;
import com.lightcomp.ft.core.send.items.SourceDir;
import com.lightcomp.ft.core.send.items.SourceItem;
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.exception.TransferExceptionBuilder;

class DirContext {

    private final String name;

    private final Path path;

    private final Iterator<SourceItem> itemIt;

    private boolean started;

    private DirContext(String name, Path path, Iterator<SourceItem> itemIt) {
        this.name = name;
        this.itemIt = itemIt;
        this.path = path;
    }

    public String getName() {
        return name;
    }

    public Path getPath() {
        return path;
    }

    public boolean hasNextItem() {
        return itemIt != null && itemIt.hasNext();
    }

    public SourceItem getNextItem() {
        return itemIt.next();
    }

    public boolean isStarted() {
        return started;
    }

    public void setStarted(boolean started) {
        this.started = started;
    }

    public static DirContext createRoot(Iterator<SourceItem> itemIt) {
        return new DirContext(null, PathUtils.ROOT, itemIt);
    }

    public static DirContext create(SourceDir srcDir, Path parentPath) throws TransferException {
        String name = srcDir.getName();
        Path path;
        try {
            path = parentPath.resolve(name);
        } catch (InvalidPathException e) {
            throw new TransferExceptionBuilder("Invalid source directory name").addParam("parentPath", parentPath)
                    .addParam("name", name).setCause(e).build();
        }
        return new DirContext(name, path, srcDir.getItemIterator());
    }
}
