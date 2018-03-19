package com.lightcomp.ft;

import java.util.Collection;
import java.util.Collections;

import com.lightcomp.ft.sender.SourceDir;
import com.lightcomp.ft.sender.SourceFile;
import com.lightcomp.ft.sender.SourceItem;

public class SourceDirImpl implements SourceDir {

    @Override
    public String getName() {
        return "TestDir";
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
    public Collection<SourceItem> getItems() {
        return Collections.emptyList();
    }

}
