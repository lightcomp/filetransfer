package com.lightcomp.ft.client.internal.upload;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;

public interface FrameBlockData {

    ReadableByteChannel openChannel() throws IOException;
}
