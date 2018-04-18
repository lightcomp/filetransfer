package com.lightcomp.ft.client.internal.upload.blocks;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.LinkedList;

import org.apache.commons.lang3.StringUtils;

import com.lightcomp.ft.client.internal.upload.FileContext;
import com.lightcomp.ft.core.SourceItem;

public class FrameBlockProvider {

    private final LinkedList<DirEntry> dirStack = new LinkedList<>();

    private FrameBlock lastBlock;

    public FrameBlockProvider(Iterator<SourceItem> rootItemIterator) {
        addDir(rootItemIterator, Paths.get(""));
    }

    public FrameBlock getNext() {
        if (lastBlock != null) {
            lastBlock = lastBlock.getNext();
        }
        if (lastBlock == null) {
            lastBlock = createBlock();
        }
        return lastBlock;
    }

    private FrameBlock createBlock() {
        if (dirStack.isEmpty()) {
            return null;
        }
        DirEntry dir = dirStack.getLast();
        if (dir.it != null && dir.it.hasNext()) {
            return createBlock(dir.it.next(), dir.path);
        }
        dirStack.removeLast();
        return new DirEndBlock();
    }

    private FrameBlock createBlock(SourceItem item, Path parentPath) {
        String name = item.getName();
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("Empty source item name, parentPath=" + parentPath);
        }
        Path path = parentPath.resolve(name);
        if (item.isDir()) {
            addDir(item.asDir().getItemIterator(), path);
            return new DirBeginBlock(name);
        }
        FileContext fc = FileContext.create(item.asFile(), path.toString());
        return new FileBeginBlock(fc);
    }

    private void addDir(Iterator<SourceItem> it, Path path) {
        DirEntry dir = new DirEntry(it, path);
        dirStack.addLast(dir);
    }

    private static class DirEntry {

        final Iterator<SourceItem> it;

        final Path path;

        DirEntry(Iterator<SourceItem> it, Path path) {
            this.it = it;
            this.path = path;
        }
    }
}
