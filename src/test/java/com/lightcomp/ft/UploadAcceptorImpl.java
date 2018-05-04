package com.lightcomp.ft;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.server.Server;
import com.lightcomp.ft.server.TransferState;
import com.lightcomp.ft.server.TransferStatus;
import com.lightcomp.ft.server.UploadAcceptor;

import net.jodah.concurrentunit.Waiter;

public class UploadAcceptorImpl implements UploadAcceptor {

    private final Logger logger = LoggerFactory.getLogger(UploadAcceptorImpl.class);
    
    private final String transferId;

    private final Path uploadDir;

    private final Server server;

    private final Waiter waiter;
    
    private TransferState progressState;

    public UploadAcceptorImpl(String transferId, Path uploadDir, Server server, Waiter waiter) {
        this.transferId = transferId;
        this.uploadDir = uploadDir;
        this.server = server;
        this.waiter = waiter;
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

        TransferState state = status.getState();
        if (progressState == null) {
            waiter.assertEquals(TransferState.STARTED, state);
            progressState = state;
            return;
        }
        waiter.assertEquals(TransferState.STARTED, progressState);

        if (state == TransferState.STARTED) {
            return;
        }
        waiter.assertEquals(TransferState.TRANSFERED, state);
        progressState = state;
    }

    @Override
    public void onTransferSuccess() {
        logger.info("Acceptor: transfer success, transferId={}", transferId);
        
        TransferStatus ts = server.getTransferStatus(transferId);
        waiter.assertEquals(TransferState.FINISHED, ts.getState());
        waiter.resume();
    }

    @Override
    public void onTransferCanceled() {
        TransferStatus ts = server.getTransferStatus(transferId);
        waiter.assertEquals(TransferState.CANCELED, ts.getState());
        waiter.fail("Acceptor: transfer canceled, transferId=" + transferId);
    }

    @Override
    public void onTransferFailed(Throwable cause) {
        TransferStatus ts = server.getTransferStatus(transferId);
        waiter.assertEquals(TransferState.FAILED, ts.getState());
        waiter.fail("Acceptor: transfer failed, transferId=" + transferId + ", detail=" + cause.getMessage());
    }
}
