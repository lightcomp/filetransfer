package com.lightcomp.ft;

import java.util.Collection;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.core.send.items.SourceItem;
import com.lightcomp.ft.server.DownloadHandler;
import com.lightcomp.ft.server.ErrorDesc;
import com.lightcomp.ft.server.Server;
import com.lightcomp.ft.server.TransferState;
import com.lightcomp.ft.server.TransferStatus;
import com.lightcomp.ft.xsd.v1.GenericDataType;

import net.jodah.concurrentunit.Waiter;

public class DwnldHandlerImpl implements DownloadHandler {

    private final Logger logger = LoggerFactory.getLogger(DwnldHandlerImpl.class);

    private final String transferId;

    private final GenericDataType response;

    private final String requestId;

    private final Collection<SourceItem> items;

    private final Server server;

    private final Waiter waiter;

    private final TransferState terminalState;

    private TransferState progressState;

    public DwnldHandlerImpl(String transferId, GenericDataType response, String requestId, Collection<SourceItem> items,
            Server server, Waiter waiter, TransferState terminalState) {
        this.transferId = transferId;
        this.response = response;
        this.requestId = requestId;
        this.items = items;
        this.server = server;
        this.waiter = waiter;
        this.terminalState = terminalState;
    }

    @Override
    public Mode getMode() {
        return Mode.DOWNLOAD;
    }

    @Override
    public String getRequestId() {
        return requestId;
    }

    @Override
    public Iterator<SourceItem> getItemIterator() {
        return items.iterator();
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
    public GenericDataType finishTransfer() {
        logger.info("Server transfer finished, transferId={}", transferId);

        TransferStatus ts = server.getTransferStatus(transferId);
        waiter.assertEquals(TransferState.FINISHING, ts.getState());

        if (terminalState != TransferState.FINISHING) {
            waiter.fail("Server transfer finished");
        } else {
            waiter.resume();
        }
        return response;
    }

    @Override
    public void onTransferCanceled() {
        logger.info("Server transfer canceled, transferId={}", transferId);

        TransferStatus ts = server.getTransferStatus(transferId);
        TransferState state = ts.getState();
        waiter.assertTrue(TransferState.CANCELED == state || TransferState.ABORTED == state);

        if (terminalState != TransferState.CANCELED && terminalState != TransferState.ABORTED) {
            waiter.fail("Server transfer canceled");
        } else {
            waiter.resume();
        }
    }

    @Override
    public void onTransferFailed(ErrorDesc errorDesc) {
        logger.info("Server transfer failed, transferId={}, desc: {}", transferId, errorDesc);

        TransferStatus ts = server.getTransferStatus(transferId);
        waiter.assertEquals(TransferState.FAILED, ts.getState());

        if (terminalState != TransferState.FAILED) {
            waiter.fail("Server transfer failed, desc: " + errorDesc);
        } else {
            waiter.resume();
        }
    }
}
