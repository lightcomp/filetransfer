package com.lightcomp.ft;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.lightcomp.ft.server.TransferAcceptor;
import com.lightcomp.ft.server.TransferReceiver;

public class SimpleUploadReceiver implements TransferReceiver {

    private List<UploadAcceptorImpl> uploadAcceptors = new ArrayList<>();

    private final Path transferDir;

    private int lastTransferId;

    public SimpleUploadReceiver(Path transferDir) {
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
        UploadAcceptorImpl acceptor = new UploadAcceptorImpl(transferId, uploadDir);
        uploadAcceptors.add(acceptor);
        return acceptor;
    }

    public synchronized boolean isTerminated() {
        uploadAcceptors.removeIf(UploadAcceptorImpl::isFinished);
        return uploadAcceptors.isEmpty();
    }
}
