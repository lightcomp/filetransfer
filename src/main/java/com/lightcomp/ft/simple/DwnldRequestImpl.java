package com.lightcomp.ft.simple;

import java.nio.file.Path;

import com.lightcomp.ft.client.AbstractRequest;
import com.lightcomp.ft.client.DownloadRequest;
import com.lightcomp.ft.xsd.v1.GenericDataType;

public class DwnldRequestImpl extends AbstractRequest implements DownloadRequest {

    private final Path downloadDir;

    public DwnldRequestImpl(Path downloadDir, GenericDataType data) {
        super(data);
        this.downloadDir = downloadDir;
    }

    @Override
    public Path getDownloadDir() {
        return downloadDir;
    }
}
