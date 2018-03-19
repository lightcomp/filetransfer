package com.lightcomp.ft.sender;

/**
 * Transfer source data.
 */
public interface SourceItem {

    String getName();

    boolean isDir();

    SourceDir asDir();

    SourceFile asFile();
}
