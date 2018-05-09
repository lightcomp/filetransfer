package com.lightcomp.ft.core.send.items;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;

public interface ChannelProvider {

    ReadableByteChannel openChannel(long position) throws IOException;
}
