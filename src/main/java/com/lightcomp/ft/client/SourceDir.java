package com.lightcomp.ft.client;

import java.util.Iterator;

/**
 * Transfer source directory.
 */
public interface SourceDir extends SourceItem {

    /**
     * Returns new iterator for directory content, not-null.
     */
    Iterator<SourceItem> getItemIterator();
}
