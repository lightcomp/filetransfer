package com.lightcomp.ft.server.impl.tasks;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.lightcomp.ft.exception.CanceledException;
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.server.impl.TransferContext;
import com.lightcomp.ft.xsd.v1.FileChecksum;

public class PrepareTask implements Task {

    private final Collection<FileChecksum> fileChecksums;

    private final TransferContext transferCtx;

    public PrepareTask(Collection<FileChecksum> fileChecksums, TransferContext transferCtx) {
        this.fileChecksums = fileChecksums;
        this.transferCtx = transferCtx;
    }

    @Override
    public void run() throws CanceledException {
        int fileCount = transferCtx.getFileCount();
        if (fileCount != fileChecksums.size()) {
            throw new TransferException("Checksums for transfered files are not complete");
        }

        Set<String> receivedIds = new HashSet<>(fileCount);
        for (FileChecksum fch : fileChecksums) {
            if (transferCtx.isCancelRequested()) {
                throw new CanceledException();
            }
            if (!receivedIds.add(fch.getFileId())) {
                throw TransferExceptionBuilder.from("Duplicate file id in checksum definition")
                        .addParam("fileId", fch.getFileId()).build();
            }
            validateChecksum(fch);
        }
    }

    private void validateChecksum(FileChecksum fch) {
        TransferFile tf = transferCtx.getFile(fch.getFileId());
        String checksum = tf.getChecksum();
        if (checksum.equals(fch.getChecksum())) {
            return;
        }
        throw TransferExceptionBuilder.from("File checksum does not match").addParam("fileId", fch.getFileId())
                .addParam("receiverChecksum", checksum).addParam("requestChecksum", fch.getChecksum()).build();
    }
}
