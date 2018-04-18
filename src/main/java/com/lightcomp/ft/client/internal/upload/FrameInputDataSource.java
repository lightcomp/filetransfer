package com.lightcomp.ft.client.internal.upload;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.util.Collection;
import java.util.List;

import javax.activation.DataSource;

class FrameInputDataSource implements DataSource {

    private final Collection<FrameBlockData> frameBlockData;

    public FrameInputDataSource(List<FrameBlockData> frameBlockData) {
        this.frameBlockData = frameBlockData;
    }

    @Override
    public String getContentType() {
        return "application/octet-stream";
    }

    @Override
    public InputStream getInputStream() throws IOException {
        FrameReadableByteChannel frbch = new FrameReadableByteChannel(frameBlockData);
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
