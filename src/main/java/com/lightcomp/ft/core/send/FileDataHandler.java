package com.lightcomp.ft.core.send;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;

public interface FileDataHandler {

    ReadableByteChannel openChannel(long position) throws IOException;
}
