package com.lightcomp.ft;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.lightcomp.ft.server.Server;
import com.lightcomp.ft.server.TransferAcceptor;
import com.lightcomp.ft.server.TransferReceiver;

import net.jodah.concurrentunit.Waiter;

public class SimpleUploadReceiver implements TransferReceiver {

    private final Path transferDir;

    private final Waiter waiter;
    
    private Server server;

    private int lastTransferId;

    public SimpleUploadReceiver(Path transferDir, Waiter waiter) {
        this.transferDir = transferDir;
        this.waiter = waiter;
    }

    public void setServer(Server server) {
        this.server = server;
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
        UploadAcceptorImpl acceptor = new UploadAcceptorImpl(transferId, uploadDir, server, waiter);
        return acceptor;
    }

    public synchronized String getLastTransferId() {
        return Integer.toString(lastTransferId);
    }
}
