package com.lightcomp.ft;

import java.util.Collection;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.client.Transfer;
import com.lightcomp.ft.client.TransferState;
import com.lightcomp.ft.client.TransferStatus;
import com.lightcomp.ft.client.UploadRequest;
import com.lightcomp.ft.core.send.items.SourceItem;

import net.jodah.concurrentunit.Waiter;

public class UploadRequestImpl implements UploadRequest {

    private final Logger logger = LoggerFactory.getLogger(UploadRequestImpl.class);

    private final String requestId;

    private final Collection<SourceItem> items;

    private final Waiter waiter;

    private final TransferState terminalState;

    private Transfer transfer;

    private TransferState progressState;

    public UploadRequestImpl(String requestId, Collection<SourceItem> items, Waiter waiter, TransferState terminalState) {
        this.requestId = requestId;
        this.items = items;
        this.waiter = waiter;
        this.terminalState = terminalState;
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
    public void onTransferInitialized(Transfer transfer) {
        waiter.assertEquals(null, this.transfer);
        waiter.assertNotNull(transfer);
        this.transfer = transfer;
    }

    @Override
    public void onTransferProgress(TransferStatus status) {
        logger.info("Client transfer progressed, transferId={}, detail: {}", transfer.getTransferId(), status);

        TransferState state = status.getState();
        if (progressState == null) {
            waiter.assertEquals(TransferState.STARTED, state);
            progressState = state;
            return;
        }
        if (state == TransferState.STARTED) {
            waiter.assertEquals(TransferState.STARTED, progressState);
            return;
        }
        waiter.assertEquals(TransferState.TRANSFERED, state);
        progressState = state;
    }

    @Override
    public void onTransferSuccess() {
        TransferStatus ts = transfer.getStatus();
        waiter.assertEquals(TransferState.FINISHED, ts.getState());

        if (terminalState != TransferState.FINISHED) {
            waiter.fail("Client transfer succeeded");
        } else {
            waiter.resume();
        }
    }

    @Override
    public void onTransferCanceled() {
        TransferStatus ts = transfer.getStatus();
        waiter.assertEquals(TransferState.CANCELED, ts.getState());

        if (terminalState != TransferState.CANCELED) {
            waiter.fail("Client transfer canceled");
        } else {
            waiter.resume();
        }
    }

    @Override
    public void onTransferFailed(Throwable cause) {
        TransferStatus ts = transfer.getStatus();
        waiter.assertEquals(TransferState.FAILED, ts.getState());

        if (terminalState != TransferState.FAILED) {
            waiter.fail("Client transfer failed, detail: " + cause.getMessage());
        } else {
            waiter.resume();
        }
    }
}