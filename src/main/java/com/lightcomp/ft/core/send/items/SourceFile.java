package com.lightcomp.ft.core.send.items;

/**
 * Transfer source file.
 */
public interface SourceFile extends SourceItem, ChannelProvider {

    long getSize();

    long getLastModified();

    byte[] getChecksum();
}
