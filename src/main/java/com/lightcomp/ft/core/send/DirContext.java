package com.lightcomp.ft.core.send;

import java.nio.file.Path;
import java.util.Iterator;

import com.lightcomp.ft.core.send.items.SourceItem;

class DirContext {

    private final Path path;

    private final Iterator<SourceItem> itemIt;

    private boolean started;

    public DirContext(Path path, Iterator<SourceItem> itemIt) {
        this.itemIt = itemIt;
        this.path = path;
    }

    public Path getPath() {
        return path;
    }

    public String getName() {
        return path.getFileName().toString();
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
}
