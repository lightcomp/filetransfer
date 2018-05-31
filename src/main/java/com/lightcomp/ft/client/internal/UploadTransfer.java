package com.lightcomp.ft.client.internal;

import com.lightcomp.ft.client.ClientConfig;
import com.lightcomp.ft.client.TransferStatus;
import com.lightcomp.ft.client.UploadRequest;
import com.lightcomp.ft.client.internal.operations.OperationResult;
import com.lightcomp.ft.client.internal.operations.OperationResult.Type;
import com.lightcomp.ft.client.internal.operations.SendOperation;
import com.lightcomp.ft.core.send.FrameBuilder;
import com.lightcomp.ft.core.send.SendFrameContext;
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
    public void onFileDataSend(long size) {
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
        FrameBuilder frameBuilder = new FrameBuilder(this, config);
        frameBuilder.init(request.getItemIterator());
        // send all frames
        while (true) {
            if (cancelIfRequested()) {
                return false;
            }
            // build frame
            SendFrameContext frameCtx = frameBuilder.build();
            // send frame
            SendOperation op = new SendOperation(this, service, frameCtx);
            OperationResult result = op.execute();
            if (result.getType() != Type.SUCCESS) {
                operationFailed(result);
                return false;
            }
            // add processed frame num
            frameProcessed(frameCtx.getSeqNum());
            // exit if last
            if (frameCtx.isLast()) {
                return true;
            }
        }
    }
}
