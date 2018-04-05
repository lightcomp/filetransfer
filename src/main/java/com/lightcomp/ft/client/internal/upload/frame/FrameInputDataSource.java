package com.lightcomp.ft.client.internal.upload.frame;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.util.Collection;
import java.util.List;

import javax.activation.DataSource;

class FrameInputDataSource implements DataSource {

    private final Collection<DataProvider> dataProviders;

    public FrameInputDataSource(List<DataProvider> dataProviders) {
        this.dataProviders = dataProviders;
    }

    @Override
    public String getContentType() {
        return "application/octet-stream";
    }

    @Override
    public InputStream getInputStream() throws IOException {
        FrameReadableByteChannel frbch = new FrameReadableByteChannel(dataProviders);
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
