package com.lightcomp.ft.simple;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

import com.lightcomp.ft.client.UploadRequest;
import com.lightcomp.ft.core.send.items.SimpleDir;
import com.lightcomp.ft.core.send.items.SimpleFile;
import com.lightcomp.ft.core.send.items.SourceItem;
import com.lightcomp.ft.xsd.v1.GenericDataType;

public class UploadRequestImpl extends AbstractRequest implements UploadRequest {

    private final Path dataDir;

    public UploadRequestImpl(Path dataDir, GenericDataType data) {
        super(data);
        this.dataDir = dataDir;
    }

    @Override
    public Iterator<SourceItem> getItemIterator() {
        try {
            return Files.list(dataDir).<SourceItem>map(p -> {
                if (Files.isDirectory(p)) {
                    return new SimpleDir(p);
                } else {
                    return new SimpleFile(p);
                }
            }).iterator();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
