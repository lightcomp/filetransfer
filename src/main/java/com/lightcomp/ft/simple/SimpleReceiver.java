package com.lightcomp.ft.simple;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.lightcomp.ft.server.TransferAcceptor;
import com.lightcomp.ft.server.TransferReceiver;
import com.lightcomp.ft.xsd.v1.GenericData;

public class SimpleReceiver implements TransferReceiver {

    private final Path transferDir;

    private int lastTransferId;

    public SimpleReceiver(Path transferDir) {
        this.transferDir = transferDir;
    }

    @Override
    public synchronized TransferAcceptor onTransferBegin(GenericData request) {
        String transferId = Integer.toString(++lastTransferId);
        Path uploadDir = transferDir.resolve(transferId);
        // prepare transfer directory
        try {
            Files.createDirectory(uploadDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        // TODO: download acceptor
        return new UploadAcceptorImpl(transferId, request, uploadDir);
    }
}
