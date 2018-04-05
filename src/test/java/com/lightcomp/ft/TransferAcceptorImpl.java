package com.lightcomp.ft;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.server.TransferAcceptor;
import com.lightcomp.ft.server.TransferStatus;

public class TransferAcceptorImpl implements TransferAcceptor {

    private final Logger logger = LoggerFactory.getLogger(TransferAcceptorImpl.class);

    private final String transferId;

    private final Path transferDir;

    public TransferAcceptorImpl(String transferId, Path transferDir) {
        this.transferId = transferId;
        this.transferDir = transferDir;
    }

    @Override
    public String getTransferId() {
        return transferId;
    }

    @Override
    public Path getTransferDir() {
        return transferDir;
    }

    @Override
    public void onTransferProgress(TransferStatus status) {
        logger.info("Receiver: transfer progress, transferId={}, detail: {}", transferId, status);
    }

    @Override
    public void onTransferSuccess() {
        logger.info("Receiver: transfer success, transferId={}", transferId);
    }

    @Override
    public void onTransferCanceled() {
        logger.warn("Receiver: transfer canceled, transferId={}", transferId);
    }

    @Override
    public void onTransferFailed(Throwable cause) {
        // logged internally
    }
}
