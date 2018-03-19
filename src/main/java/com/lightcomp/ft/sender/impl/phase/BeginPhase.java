package com.lightcomp.ft.sender.impl.phase;

import java.util.ArrayList;
import java.util.List;

import com.lightcomp.ft.exception.CanceledException;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.sender.SourceFile;
import com.lightcomp.ft.sender.TransferState;
import com.lightcomp.ft.sender.impl.TransferContext;
import com.lightcomp.ft.xsd.v1.File;
import com.lightcomp.ft.xsd.v1.FileTransfer;

import cxf.FileTransferService;

public class BeginPhase implements Phase {

    private final List<FileProvider> fileProviders = new ArrayList<>();

    private final TransferContext transferCtx;

    public BeginPhase(TransferContext transferCtx) {
        this.transferCtx = transferCtx;
    }

    @Override
    public void process() throws CanceledException {
        FileTransferService service = transferCtx.getService();
        String requestId = transferCtx.getRequestId();

        try {
            FileTransfer fileTransfer = createFileTransfer();
            String transferId = service.begin(requestId, fileTransfer);
            transferCtx.setTransferId(transferId);
        } catch (CanceledException ae) {
            throw ae;
        } catch (Throwable t) {
            throw TransferExceptionBuilder.from("Failed to begin transfer").setTransfer(transferCtx).setCause(t).build();
        }
    }

    @Override
    public Phase getNextPhase() {
        return new DataPhase(transferCtx, fileProviders);
    }

    @Override
    public TransferState getNextState() {
        return TransferState.STARTED;
    }

    private FileTransfer createFileTransfer() throws CanceledException {
        FileTransferBuilder builder = new FileTransferBuilder(transferCtx) {
            @Override
            protected File convertFile(SourceFile sf) {
                File f = super.convertFile(sf);
                handleConvertedFile(f.getFileId(), sf);
                return f;
            }
        };
        return builder.build();
    }

    private void handleConvertedFile(String fileId, SourceFile sourceFile) {
        FileProvider fp = FileProvider.create(fileId, sourceFile, transferCtx.getChecksumType());
        fileProviders.add(fp);
        transferCtx.onFilePrepared(fp.getSize());
    }
}
