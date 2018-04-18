package com.lightcomp.ft.client.internal.upload;

import com.lightcomp.ft.client.ClientConfig;
import com.lightcomp.ft.client.UploadRequest;
import com.lightcomp.ft.client.internal.AbstractTransfer;
import com.lightcomp.ft.client.internal.operations.SendOperation;
import com.lightcomp.ft.client.internal.upload.blocks.FrameBlock;
import com.lightcomp.ft.client.internal.upload.blocks.FrameBlockProvider;
import com.lightcomp.ft.exception.CanceledException;

import cxf.FileTransferService;

public class UploadTransfer extends AbstractTransfer {

    private final FrameBlockProvider frameBlockProvider;

    private int lastFrameSeqNum;

    public UploadTransfer(UploadRequest request, ClientConfig config, FileTransferService service) {
        super(request, config, service);
        this.frameBlockProvider = new FrameBlockProvider(request.getItemIterator());
    }

    @Override
    protected void transfer() throws CanceledException {
        boolean lastFrame = false;
        while (!lastFrame) {
            // increment last frame seq. number
            lastFrameSeqNum++;
            // prepare frame
            FrameContext frameCtx = new FrameContext(lastFrameSeqNum, config);
            lastFrame = addFrameBlocks(frameCtx);
            frameCtx.setLast(lastFrame);
            // send frame
            SendOperation op = new SendOperation(this, this, frameCtx);
            op.execute(service);
            // update progress
            updateProgress(frameCtx.getDataSize());
        }
    }

    /**
     * Add blocks to specified frame.
     * 
     * @return Returns true if frame-block provider is depleted (last frame).
     */
    private boolean addFrameBlocks(FrameContext frameCtx) throws CanceledException {
        while (true) {
            CanceledException.checkTransfer(this);
            FrameBlock block = frameBlockProvider.getNext();
            if (block == null) {
                return true;
            }
            if (!block.addToFrame(frameCtx)) {
                return false;
            }
        }
    }
}
