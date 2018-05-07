package com.lightcomp.ft;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.server.Server;
import com.lightcomp.ft.server.TransferState;
import com.lightcomp.ft.server.TransferStatus;

import net.jodah.concurrentunit.Waiter;

public class AcceptorInterceptorImpl implements AcceptorInterceptor {

    private final Logger logger = LoggerFactory.getLogger(AcceptorInterceptorImpl.class);

    private final Server server;

    private final Waiter waiter;

    private final String transferId;

    private final TransferState terminalState;

    private TransferState progressState;

    public AcceptorInterceptorImpl(Server server, Waiter waiter, String transferId, TransferState terminalState) {
        this.server = server;
        this.waiter = waiter;
        this.transferId = transferId;
        this.terminalState = terminalState;
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
    public void onTransferSuccess() {
        TransferStatus ts = server.getTransferStatus(transferId);
        waiter.assertEquals(TransferState.FINISHED, ts.getState());

        if (terminalState != TransferState.FINISHED) {
            waiter.fail("Server transfer succeeded");
        } else {
            waiter.resume();
        }
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
