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

    private final Collection<FileProvider> files;

    private final Iterator<FileProvider> fileIt;

    private int lastFrameId;

    private String lastSentFrameId;

    private FileProvider currFile;

    private long currFileOffset;

    public DataPhase(TransferContext transferCtx, Collection<FileProvider> files) {
        super(transferCtx);
        this.files = files;
        this.fileIt = files.iterator();
    }

    @Override
    public Phase getNextPhase() {
        return new PreparePhase(transferCtx, files);
    }

    @Override
    public TransferState getNextState() {
        return TransferState.TRANSFERED;
    }

    @Override
    protected Operation getNextOperation() throws CanceledException {
        if (!fileIt.hasNext() && currFile == null) {
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
        FrameContext frameCtx = createFrameContext();

        // add current file
        if (currFile != null) {
            long len = currFile.getSize() - currFileOffset;
            long n = frameCtx.addFile(currFile, currFileOffset, len);
            currFileOffset += n;

            if (currFileOffset < currFile.getSize()) {
                return frameCtx; // send rest of the file in next frame
            }
        }

        // fill frame with next file
        while (fileIt.hasNext()) {
            currFile = fileIt.next();
            currFileOffset = 0;

            long n = frameCtx.addFile(currFile, 0, currFile.getSize());
            currFileOffset += n;

            if (currFileOffset < currFile.getSize()) {
                return frameCtx; // send rest of the file in next frame
            }
        }

        // last file fits in to the frame
        currFile = null;

        return frameCtx;
    }

    private FrameContext createFrameContext() {
        String frameId = Integer.toString(++lastFrameId);
        return new FrameContext(frameId, transferCtx);
    }
}
