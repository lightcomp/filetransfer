package com.lightcomp.ft;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.lightcomp.ft.server.TransferAcceptor;
import com.lightcomp.ft.server.TransferReceiver;

public abstract class UploadReceiver implements TransferReceiver {

    private final Path transferDir;

    private int lastTransferId;

    public UploadReceiver(Path transferDir) {
        this.transferDir = transferDir;
    }

    @Override
    public synchronized TransferAcceptor onTransferBegin(String requestId) {
        String transferId = Integer.toString(++lastTransferId);
        Path uploadDir = transferDir.resolve(transferId);
        // prepare transfer directory
        try {
            Files.createDirectory(uploadDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        // add acceptor
        AcceptorInterceptor interceptor = createInterceptor(transferId);
        UploadAcceptorImpl acceptor = new UploadAcceptorImpl(transferId, uploadDir, interceptor);
        return acceptor;
    }

    public synchronized String getLastTransferId() {
        return Integer.toString(lastTransferId);
    }

    public abstract AcceptorInterceptor createInterceptor(String transferId);
}
