package com.lightcomp.ft.core;

import java.util.Iterator;

/**
 * Transfer source directory.
 */
public interface SourceDir extends SourceItem {

    /**
     * Returns iterator for directory content.
     */
    Iterator<SourceItem> getItemIterator();
}
