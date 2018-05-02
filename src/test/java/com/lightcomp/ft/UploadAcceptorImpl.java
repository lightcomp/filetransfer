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

    private volatile String failureMsg;

    private volatile boolean finished;

    public UploadAcceptorImpl(String transferId, Path uploadDir) {
        this.transferId = transferId;
        this.uploadDir = uploadDir;
    }

    public boolean isFinished() {
        if (failureMsg != null) {
            throw new RuntimeException(failureMsg);
        }
        return finished;
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
        logger.info("Acceptor: transfer progress, transferId={}, detail: {}", transferId, status);
    }

    @Override
    public void onTransferSuccess() {
        logger.info("Acceptor: transfer success, transferId={}", transferId);
        finished = true;
    }

    @Override
    public void onTransferCanceled() {
        failureMsg = "Acceptor: transfer canceled, transferId=" + transferId;
    }

    @Override
    public void onTransferFailed(Throwable cause) {
        failureMsg = "Acceptor: transfer failed, transferId=" + transferId + ", detail=" + cause.getMessage();
    }
}
