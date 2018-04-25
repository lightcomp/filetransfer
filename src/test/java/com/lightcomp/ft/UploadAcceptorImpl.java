package com.lightcomp.ft;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.server.TransferStatus;
import com.lightcomp.ft.server.UploadAcceptor;

public class UploadAcceptorImpl implements UploadAcceptor {

    private final Logger logger = LoggerFactory.getLogger(UploadAcceptorImpl.class);

    private final String transferId;

    private final Path uploadDir;

    private volatile boolean terminated;

    public UploadAcceptorImpl(String transferId, Path uploadDir) {
        this.transferId = transferId;
        this.uploadDir = uploadDir;
    }

    public boolean isTerminated() {
        return terminated;
    }

    @Override
    public Mode getMode() {
        return Mode.UPLOAD;
    }

    @Override
    public String getTransferId() {
        return transferId;
    }

    @Override
    public Path getUploadDir() {
        return uploadDir;
    }

    @Override
    public void onTransferProgress(TransferStatus status) {
        logger.info("Receiver: transfer progress, transferId={}, detail: {}", transferId, status);
    }

    @Override
    public void onTransferSuccess() {
        logger.info("Receiver: transfer success, transferId={}", transferId);
        terminated = true;
    }

    @Override
    public void onTransferCanceled(boolean clientAbort) {
        logger.warn("Receiver: transfer canceled, transferId={}, clientAbort={}", transferId, clientAbort);
        terminated = true;
    }

    @Override
    public void onTransferFailed(Throwable cause) {
        // logged internally
        terminated = true;
    }
}
