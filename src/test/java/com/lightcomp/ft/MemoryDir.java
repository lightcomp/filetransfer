package com.lightcomp.ft;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import com.lightcomp.ft.core.send.items.SourceDir;
import com.lightcomp.ft.core.send.items.SourceFile;
import com.lightcomp.ft.core.send.items.SourceItem;

/**
 * Directory in memory.
 * 
 * Used mainly for tests. Items have to be added manually.
 */
public class MemoryDir implements SourceDir {

    private final List<SourceItem> items = new ArrayList<>();

    private final String name;

    public MemoryDir(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
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
        return items.iterator();
    }

    public void addChild(SourceItem child) {
        items.add(child);
    }

    public void addChildren(Collection<SourceItem> children) {
        items.addAll(children);
    }

}
