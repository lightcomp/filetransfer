package com.lightcomp.ft;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.lightcomp.ft.server.TransferDataHandler;
import com.lightcomp.ft.server.TransferHandler;
import com.lightcomp.ft.server.UploadHandler;
import com.lightcomp.ft.xsd.v1.GenericData;

public abstract class UploadReceiver implements TransferHandler {

    private final Path transferDir;

    private int lastTransferId;

    public UploadReceiver(Path transferDir) {
        this.transferDir = transferDir;
    }

    @Override
    public synchronized TransferDataHandler onTransferBegin(GenericData request) {
        String transferId = Integer.toString(++lastTransferId);
        Path uploadDir = transferDir.resolve(transferId);
        // prepare transfer directory
        try {
            Files.createDirectory(uploadDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return createUploadAcceptor(transferId, uploadDir, request);
    }

    public synchronized String getLastTransferId() {
        return Integer.toString(lastTransferId);
    }

    protected abstract UploadHandler createUploadAcceptor(String transferId, Path uploadDir, GenericData request);
}
