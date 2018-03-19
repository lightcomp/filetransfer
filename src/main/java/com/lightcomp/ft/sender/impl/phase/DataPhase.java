package com.lightcomp.ft.sender.impl.phase;

import java.util.Collection;
import java.util.Iterator;

import com.lightcomp.ft.exception.CanceledException;
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.sender.TransferState;
import com.lightcomp.ft.sender.impl.TransferContext;
import com.lightcomp.ft.sender.impl.phase.frame.FrameContext;
import com.lightcomp.ft.sender.impl.phase.operation.Operation;
import com.lightcomp.ft.sender.impl.phase.operation.SendOperation;

public class DataPhase extends RecoverablePhase {

    private final Collection<FileProvider> fileProviders;

    private final Iterator<FileProvider> fileProviderIt;

    private int lastFrameId;

    private String lastSentFrameId;

    private FileProvider currFileProvider;

    private long currFileRemainingSize;

    public DataPhase(TransferContext transferCtx, Collection<FileProvider> fileProviders) {
        super(transferCtx);
        this.fileProviders = fileProviders;
        this.fileProviderIt = fileProviders.iterator();
    }

    @Override
    public Phase getNextPhase() {
        return new PreparePhase(transferCtx, fileProviders);
    }

    @Override
    public TransferState getNextState() {
        return TransferState.TRANSFERED;
    }

    @Override
    protected Operation getNextOperation() throws CanceledException {
        if (!fileProviderIt.hasNext() && currFileProvider == null) {
            return null; // no more files to send
        }
        FrameContext frameCtx = getNextFrameContext();
        return new SendOperation(this, frameCtx);
    }

    @Override
    protected TransferException createTransferException(OperationError error) {
        return TransferExceptionBuilder.from("Failed to send data").setTransfer(transferCtx).setCause(error.getCause()).build();
    }

    public String getLastSentFrameId() {
        return lastSentFrameId;
    }

    public void onSendSuccess(FrameContext frameContext) {
        lastSentFrameId = frameContext.getFrameId();

        // notify transfer context about frame success/progress
        transferCtx.onDataSent(frameContext.getSize());
    }

    private FrameContext getNextFrameContext() throws CanceledException {
        String frameId = Integer.toString(++lastFrameId);
        FrameContext frameCtx = new FrameContext(frameId, transferCtx);

        // add current file
        if (currFileProvider != null) {
            long offset = currFileProvider.getSize() - currFileRemainingSize;

            // add file to frame
            long size = frameCtx.addFile(currFileProvider, offset, currFileRemainingSize);
            currFileRemainingSize -= size;

            // test if current file does not fit in frame
            if (currFileRemainingSize > 0) {
                return frameCtx;
            }
        }

        // continue with file iterator
        while (fileProviderIt.hasNext()) {
            currFileProvider = fileProviderIt.next();
            currFileRemainingSize = currFileProvider.getSize();

            // add file to frame
            long size = frameCtx.addFile(currFileProvider, 0, currFileRemainingSize);
            currFileRemainingSize -= size;

            // test if current file does not fit in frame
            if (currFileRemainingSize > 0) {
                return frameCtx;
            }
        }

        // current file will be completely send
        currFileProvider = null;

        return frameCtx;
    }
}
