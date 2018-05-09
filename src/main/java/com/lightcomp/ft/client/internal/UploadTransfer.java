package com.lightcomp.ft.client.internal;

import com.lightcomp.ft.client.ClientConfig;
import com.lightcomp.ft.client.TransferStatus;
import com.lightcomp.ft.client.UploadRequest;
import com.lightcomp.ft.client.operations.SendOperation;
import com.lightcomp.ft.core.send.FrameBlockBuilder;
import com.lightcomp.ft.core.send.SendProgressInfo;
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
    protected boolean transferFrames() {
        FrameBlockBuilder builder = new FrameBlockBuilder(request.getItemIterator(), this);
        int lastSeqNum = 1;
        while (true) {
            // prepare frame
            UploadFrameContext frameCtx = new UploadFrameContext(lastSeqNum, config);
            builder.build(frameCtx);
            // send frame
            SendOperation op = new SendOperation(this, this, frameCtx);
            if (!op.execute(service)) {
                return false;
            }
            // exit if last
            if (frameCtx.isLast()) {
                return true;
            }
            // increment last frame number
            lastSeqNum++;
        }
    }
}
