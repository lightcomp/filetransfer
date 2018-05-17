package com.lightcomp.ft.simple;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.lightcomp.ft.server.TransferDataHandler;
import com.lightcomp.ft.server.TransferHandler;
import com.lightcomp.ft.xsd.v1.GenericDataType;

class TransferHandlerImpl implements TransferHandler {

    private final Path transferDir;

    public TransferHandlerImpl(Path transferDir) {
        this.transferDir = transferDir;
    }

    @Override
    public synchronized TransferDataHandler onTransferBegin(String transferId, GenericDataType request) {
        Path uploadDir = transferDir.resolve(transferId);
        // prepare transfer directory
        try {
            Files.createDirectory(uploadDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        // TODO: download impl
        return new UploadHandlerImpl(transferId, request, uploadDir);
    }
}
