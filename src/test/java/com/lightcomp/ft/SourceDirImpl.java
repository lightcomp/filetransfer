package com.lightcomp.ft;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.lightcomp.ft.sender.SourceDir;
import com.lightcomp.ft.sender.SourceFile;
import com.lightcomp.ft.sender.SourceItem;

public class SourceDirImpl implements SourceDir {

    private final List<SourceItem> children = new ArrayList<>();

    private final String name;

    public SourceDirImpl(String name) {
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

    public void addChild(SourceItem child) {
        children.add(child);
    }

    public void addChildren(Collection<SourceItem> children) {
        this.children.addAll(children);
    }

    @Override
    public Collection<SourceItem> getItems() {
        return children;
    }
}
