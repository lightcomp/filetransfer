package com.lightcomp.ft.core.sender.items;

/**
 * Transfer source data.
 */
public interface SourceItem {

    /**
     * Name of directory or file.
     */
    String getName();

    boolean isDir();

    SourceDir asDir();

    SourceFile asFile();
}
