package com.lightcomp.ft.client.internal.upload.frame;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;

public interface DataProvider {

    long getSize();

    ReadableByteChannel openChannel() throws IOException;
}
