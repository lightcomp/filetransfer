package com.lightcomp.ft.sender;

import java.util.Collection;

/**
 * Transfer source directory.
 */
public interface SourceDir extends SourceItem {

    Collection<SourceItem> getItems();
}
