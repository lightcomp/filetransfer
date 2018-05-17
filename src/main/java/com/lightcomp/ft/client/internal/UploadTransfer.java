package com.lightcomp.ft.client.internal;

import com.lightcomp.ft.client.ClientConfig;
import com.lightcomp.ft.client.TransferStatus;
import com.lightcomp.ft.client.UploadRequest;
import com.lightcomp.ft.client.operations.OperationStatus;
import com.lightcomp.ft.client.operations.OperationStatus.Type;
import com.lightcomp.ft.client.operations.SendOperation;
import com.lightcomp.ft.core.send.FrameBlockBuilder;
import com.lightcomp.ft.core.send.SendProgressInfo;
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.wsdl.v1.FileTransferService;

public class UploadTransfer extends AbstractTransfer implements SendProgressInfo {

    private final UploadRequest request;

    public UploadTransfer(UploadRequest request, ClientConfig config, FileTransferService service) {
        super(request, config, service);
        this.request = request;
    }

    @Override
    public void onDataSend(long size) {
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
        FrameBlockBuilder fbr = new FrameBlockBuilder(request.getItemIterator(), this);
        int currSeqNum = 1;
        while (true) {
            if (cancelIfRequested()) {
                return false;
            }
            // prepare frame
            UploadFrameContext frameCtx = new UploadFrameContext(currSeqNum, config);
            fbr.build(frameCtx);
            // send frame
            SendOperation so = new SendOperation(this, service, frameCtx);
            OperationStatus sos = so.execute();
            if (sos.getType() != Type.SUCCESS) {
                transferFailed(sos);
                return false;
            }
            // exit if last
            if (frameCtx.isLast()) {
                return true;
            }
            // increment frame number
            currSeqNum++;
        }
    }
}
