package com.lightcomp.ft.client.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.client.ClientConfig;
import com.lightcomp.ft.client.DownloadRequest;
import com.lightcomp.ft.client.TransferStatus;
import com.lightcomp.ft.client.operations.RecvOperation;
import com.lightcomp.ft.common.PathUtils;
import com.lightcomp.ft.core.recv.RecvContext;
import com.lightcomp.ft.core.recv.RecvContextImpl;
import com.lightcomp.ft.core.recv.RecvFrameProcessor;
import com.lightcomp.ft.core.recv.RecvProgressInfo;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.wsdl.v1.FileTransferService;

public class DownloadTransfer extends AbstractTransfer implements RecvProgressInfo {

    private static final Logger logger = LoggerFactory.getLogger(DownloadTransfer.class);

    private final Path downloadDir;

    private Path tempDir;

    protected DownloadTransfer(DownloadRequest request, ClientConfig config, FileTransferService service) {
        super(request, config, service);
        this.downloadDir = request.getDownloadDir();
    }

    @Override
    public void onDataReceived(long size) {
        TransferStatus ts;
        synchronized (this) {
            // update current state
            status.addTransferedData(size);
            // copy status in synch block
            ts = status.copy();
        }
        request.onTransferProgress(ts);
    }

    @Override
    protected boolean transferFrames() {
        createTempDir();
        try {
            RecvContext recvCtx = new RecvContextImpl(this, downloadDir);
            return downloadFrames(recvCtx);
        } finally {
            clearResources();
        }
    }

    private boolean downloadFrames(RecvContext recvCtx) {
        int lastSeqNum = 1;
        while (true) {
            // receive frame
            RecvOperation op = new RecvOperation(this, this, lastSeqNum);
            if (!op.execute(service)) {
                return false;
            }
            // process response
            RecvFrameProcessor rfp = RecvFrameProcessor.create(op.getResponse(), recvCtx);
            rfp.transfer(tempDir);
            rfp.process();
            // exit if last
            if (rfp.isLast()) {
                return true;
            }
            // increment last frame number
            lastSeqNum++;
        }
    }

    private void createTempDir() {
        Validate.isTrue(tempDir == null);
        try {
            tempDir = Files.createTempDirectory(transferId);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void clearResources() {
        // delete temporary files
        if (tempDir != null) {
            try {
                PathUtils.deleteWithChildren(tempDir);
            } catch (IOException e) {
                TransferExceptionBuilder.from("Failed to delete temporary download files", this).setCause(e).log(logger);
            }
        }
    }
}
