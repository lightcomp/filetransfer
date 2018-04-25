package com.lightcomp.ft;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.lightcomp.ft.server.TransferAcceptor;
import com.lightcomp.ft.server.TransferReceiver;

public class TransferReceiverImpl implements TransferReceiver {

    private final Path transferDir;

    private int lastTransferId;

    private UploadAcceptorImpl lastAcceptor;

    public TransferReceiverImpl(Path transferDir) {
        this.transferDir = transferDir;
    }

    @Override
    public TransferAcceptor onTransferBegin(String requestId) {
        String tid = Integer.toString(++lastTransferId);
        Path dir = transferDir.resolve(tid);
        // prepare transfer directory
        try {
            Files.createDirectory(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (lastAcceptor != null && !lastAcceptor.isTerminated()) {
            throw new IllegalStateException();
        }
        lastAcceptor = new UploadAcceptorImpl(tid, dir);
        return lastAcceptor;
    }

    public boolean isTerminated() {
        return lastAcceptor == null || lastAcceptor.isTerminated();
    }
}
