package com.lightcomp.ft.core.send.items;

import java.util.Iterator;

/**
 * Transfer source directory.
 */
public interface SourceDir extends SourceItem {

    /**
     * Returns new item iterator for directory content.
     */
    Iterator<SourceItem> getItemIterator();
}
