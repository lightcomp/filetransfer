package com.lightcomp.ft;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.lightcomp.ft.server.TransferDataHandler;
import com.lightcomp.ft.server.TransferHandler;
import com.lightcomp.ft.server.UploadHandler;
import com.lightcomp.ft.xsd.v1.GenericDataType;

public abstract class UploadTransferHandler implements TransferHandler {

    private final Path transferDir;

    private String lastTransferId;

    public UploadTransferHandler(Path transferDir) {
        this.transferDir = transferDir;
    }

    @Override
    public synchronized TransferDataHandler onTransferBegin(String transferId, GenericDataType request) {
        // TODO: download impl

        lastTransferId = transferId;
        Path uploadDir = transferDir.resolve(transferId);
        // prepare transfer directory
        try {
            Files.createDirectory(uploadDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return createUpload(transferId, uploadDir, request);
    }

    public synchronized String getLastTransferId() {
        return lastTransferId;
    }

    protected abstract UploadHandler createUpload(String transferId, Path uploadDir, GenericDataType request);
}
