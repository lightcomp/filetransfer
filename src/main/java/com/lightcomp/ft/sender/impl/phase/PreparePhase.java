package com.lightcomp.ft.sender.impl.phase;

import java.util.Collection;
import java.util.List;

import com.lightcomp.ft.exception.CanceledException;
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.sender.TransferState;
import com.lightcomp.ft.sender.impl.TransferContext;
import com.lightcomp.ft.sender.impl.phase.operation.Operation;
import com.lightcomp.ft.sender.impl.phase.operation.PrepareOperation;
import com.lightcomp.ft.xsd.v1.FileChecksum;
import com.lightcomp.ft.xsd.v1.FileChecksums;

public class PreparePhase extends RecoverablePhase {

    private final Collection<FileProvider> fileProviders;

    private boolean opCreated;

    public PreparePhase(TransferContext transferContext, Collection<FileProvider> fileProviders) {
        super(transferContext);
        this.fileProviders = fileProviders;
    }

    @Override
    public Phase getNextPhase() {
        return new CommitPhase(transferCtx);
    }

    @Override
    public TransferState getNextState() {
        return TransferState.PREPARED;
    }

    @Override
    protected Operation getNextOperation() {
        if (opCreated) {
            return null;
        }
        opCreated = true;
        return new PrepareOperation(this);
    }

    @Override
    protected TransferException createTransferException(OperationError error) {
        return TransferExceptionBuilder.from("Failed to prepare transfer").setTransfer(transferCtx).setCause(error.getCause()).build();
    }

    public FileChecksums createFileChecksums() throws CanceledException {
        FileChecksums fchs = new FileChecksums();
        List<FileChecksum> list = fchs.getList();

        for (FileProvider fp : fileProviders) {
            if (transferCtx.isCancelPending()) {
                throw new CanceledException();
            }
            FileChecksum fch = createFileChecksum(fp);
            list.add(fch);
        }

        return fchs;
    }

    private FileChecksum createFileChecksum(FileProvider fp) {
        FileChecksum fch = new FileChecksum();
        fch.setChecksum(fp.getChecksum());
        fch.setFileId(fp.getFileId());
        return fch;
    }
}
