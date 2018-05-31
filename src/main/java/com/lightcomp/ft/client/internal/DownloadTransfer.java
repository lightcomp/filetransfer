package com.lightcomp.ft.client.internal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.client.ClientConfig;
import com.lightcomp.ft.client.DownloadRequest;
import com.lightcomp.ft.client.TransferStatus;
import com.lightcomp.ft.client.internal.operations.OperationResult.Type;
import com.lightcomp.ft.client.internal.operations.ReceiveOperation;
import com.lightcomp.ft.client.internal.operations.ReceiveResult;
import com.lightcomp.ft.common.PathUtils;
import com.lightcomp.ft.core.recv.RecvContext;
import com.lightcomp.ft.core.recv.RecvContextImpl;
import com.lightcomp.ft.core.recv.RecvFrameProcessor;
import com.lightcomp.ft.core.recv.RecvProgressInfo;
import com.lightcomp.ft.exception.TransferExBuilder;
import com.lightcomp.ft.exception.TransferException;
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
    public void onFileDataReceived(long size) {
        TransferStatus ts;
        synchronized (this) {
            status.addTransferedData(size);
            // copy status in synch block
            ts = status.copy();
        }
        // any exception is caught in run() and transfer fails
        onTransferProgress(ts);
    }

    @Override
    protected boolean transferFrames() throws TransferException {
        try {
            prepareTempDir();
            return transferFramesInternal();
        } finally {
            deleteTempDir();
        }
    }

    private void prepareTempDir() throws TransferException {
        Validate.isTrue(tempDir == null);
        try {
            tempDir = Files.createTempDirectory(config.getWorkDir(), transferId);
        } catch (IOException e) {
            throw new TransferExBuilder("Failed to create temporary download directory", this).setCause(e).build();
        }
    }

    private void deleteTempDir() {
        if (tempDir != null) {
            try {
                PathUtils.deleteWithChildren(tempDir);
            } catch (IOException e) {
                TransferExBuilder teb = new TransferExBuilder("Failed to delete temporary download directory", this)
                        .setCause(e);
                teb.log(logger);
            }
        }
    }

    private boolean transferFramesInternal() throws TransferException {
        RecvContext recvCtx = new RecvContextImpl(this, downloadDir, config.getChecksumAlg());
        int currSeqNum = 0;
        while (true) {
            if (cancelIfRequested()) {
                return false;
            }
            // increment frame number
            currSeqNum++;
            // receive frame
            ReceiveOperation op = new ReceiveOperation(this, service, currSeqNum);
            ReceiveResult result = op.execute();
            if (result.getType() != Type.SUCCESS) {
                operationFailed(result);
                return false;
            }
            // process frame
            RecvFrameProcessor rfp = RecvFrameProcessor.create(recvCtx, result.getFrame());
            rfp.prepareData(tempDir);
            rfp.process();
            // add processed frame num
            frameProcessed(currSeqNum);
            // exit if last
            if (rfp.isLast()) {
                return true;
            }
        }
    }
}
