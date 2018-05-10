package com.lightcomp.ft.core.send.items;

import com.lightcomp.ft.core.send.FileDataHandler;

/**
 * Transfer source file.
 */
public interface SourceFile extends SourceItem, FileDataHandler {

    long getSize();

    long getLastModified();

    byte[] getChecksum();
}
