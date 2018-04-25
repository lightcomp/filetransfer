package com.lightcomp.ft.client.internal;

import com.lightcomp.ft.client.ClientConfig;
import com.lightcomp.ft.client.UploadRequest;
import com.lightcomp.ft.client.internal.operations.SendOperation;
import com.lightcomp.ft.core.sender.FrameBlockBuilder;
import com.lightcomp.ft.exception.CanceledException;
import com.lightcomp.ft.wsdl.v1.FileTransferService;

public class UploadTransfer extends AbstractTransfer {

    private final FrameBlockBuilder frameBlockBuilder;

    private int lastFrameSeqNum;

    public UploadTransfer(UploadRequest request, ClientConfig config, FileTransferService service) {
        super(request, config, service);
        this.frameBlockBuilder = new FrameBlockBuilder(request.getItemIterator());
    }

    @Override
    protected void transfer() throws CanceledException {
        boolean lastFrame = false;
        while (!lastFrame) {
            // increment last frame seq. number
            lastFrameSeqNum++;
            // prepare frame
            UploadFrameContext frameCtx = new UploadFrameContext(lastFrameSeqNum, config);
            frameBlockBuilder.build(frameCtx);
            lastFrame = frameCtx.isLast();
            // send frame
            SendOperation op = new SendOperation(this, this, frameCtx);
            if (!op.execute(service)) {
                throw new CanceledException();
            }
            // update progress
            updateProgress(frameCtx.getDataSize());
        }
    }
}
