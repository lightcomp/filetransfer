package com.lightcomp.ft.sender;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.attribute.FileTime;

/**
 * Transfer source file.
 */
public interface SourceFile extends SourceItem {

    long getSize();

    FileTime getLastModified();

    String getChecksum();

    SeekableByteChannel openChannel() throws IOException;
}
