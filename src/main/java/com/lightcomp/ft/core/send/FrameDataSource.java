package com.lightcomp.ft.core.send;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;

import javax.activation.DataSource;

public class FrameDataSource implements DataSource {

    private final Collection<BlockStreamProvider> streamProviders;

    public FrameDataSource(Collection<BlockStreamProvider> streamProviders) {
        this.streamProviders = streamProviders;
    }

    @Override
    public String getContentType() {
        return "application/octet-stream";
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new FrameInputStream(streamProviders);
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
