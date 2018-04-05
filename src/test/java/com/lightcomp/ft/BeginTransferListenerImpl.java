package com.lightcomp.ft;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.lightcomp.ft.server.BeginTransferListener;
import com.lightcomp.ft.server.TransferAcceptor;

public class BeginTransferListenerImpl implements BeginTransferListener {

    private final Path transferDir;

    private int lastTransferId;

    public BeginTransferListenerImpl(Path transferDir) {
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
        return new TransferAcceptorImpl(tid, dir);
    }
}
