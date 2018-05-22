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
import com.lightcomp.ft.client.operations.OperationStatus;
import com.lightcomp.ft.client.operations.OperationStatus.Type;
import com.lightcomp.ft.client.operations.RecvOperation;
import com.lightcomp.ft.common.Checksum;
import com.lightcomp.ft.common.PathUtils;
import com.lightcomp.ft.core.recv.RecvContext;
import com.lightcomp.ft.core.recv.RecvContextImpl;
import com.lightcomp.ft.core.recv.RecvFrameProcessor;
import com.lightcomp.ft.core.recv.RecvProgressInfo;
import com.lightcomp.ft.exception.TransferException;
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
    protected boolean transferFrames() throws TransferException {
        try {
            createTempDir();
            RecvContext recvCtx = new RecvContextImpl(this, downloadDir, Checksum.Algorithm.SHA_512);
            return downloadFrames(recvCtx);
        } finally {
            deleteTempDir();
        }
    }

    private boolean downloadFrames(RecvContext recvCtx) throws TransferException {
        int currSeqNum = 1;
        while (true) {
            if (cancelIfRequested()) {
                return false;
            }
            // receive frame
            RecvOperation ro = new RecvOperation(this, service, currSeqNum);
            OperationStatus ros = ro.execute();
            if (ros.getType() != Type.SUCCESS) {
                transferFailed(ros);
                return false;
            }
            // process frame
            RecvFrameProcessor rfp = RecvFrameProcessor.create(recvCtx, ro.getFrame());
            rfp.prepareData(tempDir);
            rfp.process();
            // add processed frame num
            frameProcessed(currSeqNum);
            // exit if last
            if (rfp.isLast()) {
                return true;
            }
            // increment frame number
            currSeqNum++;
        }
    }

    private void createTempDir() throws TransferException {
        Validate.isTrue(tempDir == null);
        try {
            tempDir = Files.createTempDirectory(config.getWorkDir(), transferId);
        } catch (IOException e) {
            throw new TransferExceptionBuilder("Failed to create temporary download directory", this).setCause(e)
                    .build();
        }
    }

    private void deleteTempDir() {
        if (tempDir != null) {
            try {
                PathUtils.deleteWithChildren(tempDir);
            } catch (IOException e) {
                new TransferExceptionBuilder("Failed to delete temporary download directory", this).setCause(e)
                        .log(logger);
            }
        }
    }
}
