package com.lightcomp.ft.core.send;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;

import javax.activation.DataSource;

public class FrameDataSource implements DataSource {

    private final Collection<FrameBlockStream> blockStreams;

    public FrameDataSource(List<FrameBlockStream> blockStreams) {
        this.blockStreams = blockStreams;
    }

    @Override
    public String getContentType() {
        return "application/octet-stream";
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new FrameInputStream(blockStreams);
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
