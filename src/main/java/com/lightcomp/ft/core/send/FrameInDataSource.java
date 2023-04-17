package com.lightcomp.ft.core.send;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;

import jakarta.activation.DataSource;

public class FrameInDataSource implements DataSource {

    private final Collection<BlockStreamProvider> bsProviders;

    private final DataSendFailureCallback failureCallback;

    public FrameInDataSource(Collection<BlockStreamProvider> bsProviders, DataSendFailureCallback failureCallback) {
        this.bsProviders = bsProviders;
        this.failureCallback = failureCallback;
    }

    @Override
    public String getContentType() {
        return "application/octet-stream";
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new FrameInStream(bsProviders, failureCallback);
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
