package com.lightcomp.ft;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.client.DownloadRequest;
import com.lightcomp.ft.client.Transfer;
import com.lightcomp.ft.client.TransferState;
import com.lightcomp.ft.client.TransferStatus;
import com.lightcomp.ft.xsd.v1.GenericDataType;

import net.jodah.concurrentunit.Waiter;

public class DwnldRequestImpl implements DownloadRequest {

    private final Logger logger = LoggerFactory.getLogger(DwnldRequestImpl.class);

    private final GenericDataType data;

    private final Path downloadDir;

    private final Waiter waiter;

    private final TransferState terminalState;

    protected Transfer transfer;

    private TransferState progressState;

    public DwnldRequestImpl(GenericDataType data, Path downloadDir, Waiter waiter, TransferState terminalState) {
        this.data = data;
        this.downloadDir = downloadDir;
        this.waiter = waiter;
        this.terminalState = terminalState;
    }

    @Override
    public GenericDataType getData() {
        return data;
    }

    @Override
    public String getLogId() {
        return data.getId();
    }

    @Override
    public Path getDownloadDir() {
        return downloadDir;
    }

    @Override
    public void onTransferInitialized(Transfer transfer) {
        waiter.assertEquals(null, this.transfer);
        waiter.assertNotNull(transfer);
        this.transfer = transfer;
    }

    @Override
    public void onTransferProgress(TransferStatus status) {
        logger.info("Client transfer progressed, detail: {}", status);

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
    public void onTransferSuccess(GenericDataType response) {
        logger.info("Client transfer succeeded, response: {}", response);
        
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
        logger.info("Client transfer canceled");
        
        TransferStatus ts = transfer.getStatus();
        waiter.assertEquals(TransferState.CANCELED, ts.getState());

        if (terminalState != TransferState.CANCELED) {
            waiter.fail("Client transfer canceled");
        } else {
            waiter.resume();
        }
    }

    @Override
    public void onTransferFailed() {
        logger.info("Client transfer failed");
        
        TransferStatus ts = transfer.getStatus();
        waiter.assertEquals(TransferState.FAILED, ts.getState());

        if (terminalState != TransferState.FAILED) {
            waiter.fail("Client transfer failed");
        } else {
            waiter.resume();
        }
    }
}
