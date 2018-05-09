package com.lightcomp.ft.client.internal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.client.ClientConfig;
import com.lightcomp.ft.client.DownloadRequest;
import com.lightcomp.ft.client.operations.RecvOperation;
import com.lightcomp.ft.core.recv.RecvContext;
import com.lightcomp.ft.core.recv.RecvContextImpl;
import com.lightcomp.ft.core.recv.RecvFrameProcessor;
import com.lightcomp.ft.core.recv.RecvProgressInfo;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.wsdl.v1.FileTransferService;

public class DownloadTransfer extends AbstractTransfer implements RecvProgressInfo {

    private static final Logger logger = LoggerFactory.getLogger(DownloadTransfer.class);

    private final Path downloadDir;

    protected DownloadTransfer(DownloadRequest request, ClientConfig config, FileTransferService service) {
        super(request, config, service);
        this.downloadDir = request.getDownloadDir();
    }

    @Override
    public void onDataReceived(long size) {
        // TODO Auto-generated method stub

    }

    @Override
    protected boolean transferFrames() {
        Path workDir = createWorkDir();
        try {
            RecvContext recvCtx = new RecvContextImpl(this, downloadDir);
            return download(recvCtx, workDir);
        } finally {
            clearResources(workDir);
        }
    }

    private boolean download(RecvContext recvCtx, Path workDir) {
        int lastSeqNum = 1;
        while (true) {
            // receive frame
            RecvOperation op = new RecvOperation(this, this, lastSeqNum);
            if (!op.execute(service)) {
                return false;
            }
            // process response
            RecvFrameProcessor rfp = new RecvFrameProcessor(op.getResponse(), recvCtx);
            rfp.transfer(workDir);
            rfp.process();
            // exit if last
            if (rfp.isLast()) {
                return true;
            }
            // increment last frame number
            lastSeqNum++;
        }
    }

    private Path createWorkDir() {
        // TODO Auto-generated method stub
        return null;
    }

    private void clearResources(Path workDir) {
        // delete temporary files
        if (workDir != null) {
            try {
                // create iterator with directory at last position
                Iterator<Path> itemIt = Files.walk(workDir).sorted(Comparator.reverseOrder()).iterator();
                // delete all (directory included)
                while (itemIt.hasNext()) {
                    Files.delete(itemIt.next());
                }
            } catch (Throwable t) {
                TransferExceptionBuilder.from("Failed to delete download temporary files", this).setCause(t).log(logger);
            }
        }
    }
}
