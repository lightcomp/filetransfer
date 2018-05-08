package com.lightcomp.ft;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.server.Server;
import com.lightcomp.ft.server.TransferState;
import com.lightcomp.ft.server.TransferStatus;
import com.lightcomp.ft.server.UploadAcceptor;
import com.lightcomp.ft.xsd.v1.GenericData;

import net.jodah.concurrentunit.Waiter;

public class UploadAcceptorImpl implements UploadAcceptor {

    private final Logger logger = LoggerFactory.getLogger(UploadAcceptorImpl.class);

    private final String transferId;

    private final GenericData response;

    private final String requestId;

    private final Path uploadDir;

    private final Server server;

    private final Waiter waiter;

    private final TransferState terminalState;

    private TransferState progressState;

    public UploadAcceptorImpl(String transferId, GenericData response, String requestId, Path uploadDir, Server server,
            Waiter waiter, TransferState terminalState) {
        this.transferId = transferId;
        this.response = response;
        this.requestId = requestId;
        this.uploadDir = uploadDir;
        this.server = server;
        this.waiter = waiter;
        this.terminalState = terminalState;
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
    public String getRequestId() {
        return requestId;
    }

    @Override
    public Path getUploadDir() {
        return uploadDir;
    }

    @Override
    public void onTransferProgress(TransferStatus status) {
        logger.info("Server transfer progressed, transferId={}, detail: {}", transferId, status);

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
    public GenericData onTransferSuccess() {
        TransferStatus ts = server.getTransferStatus(transferId);
        waiter.assertEquals(TransferState.FINISHING, ts.getState());

        if (terminalState != TransferState.FINISHING) {
            waiter.fail("Server transfer succeeded");
        } else {
            waiter.resume();
        }
        return response;
    }

    @Override
    public void onTransferCanceled() {
        TransferStatus ts = server.getTransferStatus(transferId);
        waiter.assertEquals(TransferState.CANCELED, ts.getState());

        if (terminalState != TransferState.CANCELED) {
            waiter.fail("Server transfer canceled");
        } else {
            waiter.resume();
        }
    }

    @Override
    public void onTransferFailed(Throwable cause) {
        TransferStatus ts = server.getTransferStatus(transferId);
        waiter.assertEquals(TransferState.FAILED, ts.getState());

        if (terminalState != TransferState.FAILED) {
            waiter.fail("Server transfer failed, detail: " + cause.getMessage());
        } else {
            waiter.resume();
        }
    }
}
