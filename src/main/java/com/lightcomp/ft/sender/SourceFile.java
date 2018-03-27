package com.lightcomp.ft.sender;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;

/**
 * Transfer source file.
 */
public interface SourceFile extends SourceItem {

    long getSize();

    long getLastModified();

    String getChecksum();

    ReadableByteChannel openChannel(long position) throws IOException;
}
