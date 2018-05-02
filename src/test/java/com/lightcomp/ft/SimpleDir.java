package com.lightcomp.ft;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import com.lightcomp.ft.core.send.items.SourceDir;
import com.lightcomp.ft.core.send.items.SourceFile;
import com.lightcomp.ft.core.send.items.SourceItem;

public class SimpleDir implements SourceDir {

    private final List<SourceItem> children = new ArrayList<>();

    private final String name;

    public SimpleDir(String name) {
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

    public void addChild(SourceItem item) {
        children.add(item);
    }

    public void addChildren(Collection<SourceItem> items) {
        children.addAll(items);
    }

    @Override
    public Iterator<SourceItem> getItemIterator() {
        return children.iterator();
    }
}
