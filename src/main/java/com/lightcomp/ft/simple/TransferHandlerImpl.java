package com.lightcomp.ft.simple;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.lightcomp.ft.server.TransferDataHandler;
import com.lightcomp.ft.server.TransferDataHandler.Mode;
import com.lightcomp.ft.server.TransferHandler;
import com.lightcomp.ft.xsd.v1.GenericDataType;

class TransferHandlerImpl implements TransferHandler {

    private final Path workDir;

    public TransferHandlerImpl(Path workDir) {
        this.workDir = workDir;
    }

    @Override
    public synchronized TransferDataHandler onTransferBegin(String transferId, GenericDataType request) {
        Path transferDir = workDir.resolve(request.getId());
        Mode mode = Mode.valueOf(request.getType());
        if (mode == Mode.UPLOAD) {
            try {
                Files.createDirectory(transferDir);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return new UploadHandlerImpl(transferId, request, transferDir);
        }
        return new DwnldHandlerImpl(transferId, request, transferDir);
    }
}
