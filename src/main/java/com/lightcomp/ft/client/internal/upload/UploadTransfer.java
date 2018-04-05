package com.lightcomp.ft.client.internal.upload;

import com.lightcomp.ft.client.ClientConfig;
import com.lightcomp.ft.client.SourceItem;
import com.lightcomp.ft.client.TransferState;
import com.lightcomp.ft.client.UploadRequest;
import com.lightcomp.ft.client.internal.AbstractTransfer;
import com.lightcomp.ft.client.internal.operations.SendOperation;
import com.lightcomp.ft.client.internal.upload.blocks.Block;
import com.lightcomp.ft.client.internal.upload.blocks.DirBlock;
import com.lightcomp.ft.client.internal.upload.blocks.FileBlock;
import com.lightcomp.ft.client.internal.upload.frame.FrameContext;
import com.lightcomp.ft.exception.CanceledException;

import cxf.FileTransferService;

public class UploadTransfer extends AbstractTransfer {

    private final SourceItemIterator itemIt;

    private Block currBlock;

    private int lastFrameId;

    public UploadTransfer(UploadRequest request, ClientConfig config, FileTransferService service) {
        super(request, config, service);
        this.itemIt = new SourceItemIterator(request.getItemIterator());
    }

    @Override
    protected void transfer() throws CanceledException {
        boolean lastFrame = false;
        while (!lastFrame) {
            // prepare frame
            FrameContext frameCtx = new FrameContext(++lastFrameId, config);
            lastFrame = initFrameBlocks(frameCtx);
            frameCtx.setLast(lastFrame);
            // send frame
            SendOperation op = new SendOperation(frameCtx, transferId, service);
            opDispatcher.dispatch(op);
            // update progress
            onDataSent(frameCtx.getSize());
        }
        updateState(TransferState.TRANSFERED);
    }

    private boolean initFrameBlocks(FrameContext frameCtx) throws CanceledException {
        while (true) {
            if (cancelRequested) {
                throw new CanceledException();
            }
            if (currBlock == null) {
                if (!itemIt.hasNext()) {
                    return true;
                }
                currBlock = createBlock(itemIt.next());
            }
            if (!currBlock.create(frameCtx)) {
                return false;
            }
            currBlock = currBlock.getNext();
        }
    }

    private Block createBlock(SourceItemNode itemNode) {
        SourceItem item = itemNode.getItem();
        if (item.isDir()) {
            return new DirBlock(itemNode.getItemId(), itemNode.getParentItemId(), item.asDir());
        }
        return new FileBlock(itemNode.getItemId(), itemNode.getParentItemId(), item.asFile());
    }
}
