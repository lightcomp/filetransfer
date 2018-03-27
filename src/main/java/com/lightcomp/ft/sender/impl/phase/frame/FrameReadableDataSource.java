package com.lightcomp.ft.sender.impl.phase.frame;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.util.Collection;

import javax.activation.DataSource;

public class FrameReadableDataSource implements DataSource {

    private final Collection<FrameBlockContext> blocks;

    public FrameReadableDataSource(Collection<FrameBlockContext> blocks) {
        this.blocks = blocks;
    }

    @Override
    public String getContentType() {
        return "application/octet-stream";
    }

    @Override
    public InputStream getInputStream() throws IOException {
        FrameReadableByteChannel frbch = new FrameReadableByteChannel(blocks);
        InputStream is = Channels.newInputStream(frbch);
        return is;
    }

    @Override
    public String getName() {
        return "FrameData";
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        throw new UnsupportedOperationException();
    }
}
