package com.lightcomp.ft.simple;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.client.Transfer;
import com.lightcomp.ft.client.TransferStatus;
import com.lightcomp.ft.client.UploadRequest;
import com.lightcomp.ft.core.send.items.SimpleDir;
import com.lightcomp.ft.core.send.items.SimpleFile;
import com.lightcomp.ft.core.send.items.SourceItem;
import com.lightcomp.ft.xsd.v1.GenericDataType;

public class UploadRequestImpl implements UploadRequest {

    private static final Logger logger = LoggerFactory.getLogger(UploadRequestImpl.class);

    private final Path dataDir;

    private final GenericDataType data;

    private Transfer transfer;

    public UploadRequestImpl(Path dataDir, GenericDataType data) {
        this.dataDir = dataDir;
        this.data = data;
    }

    @Override
    public GenericDataType getData() {
        return data;
    }

    @Override
    public String getLogId() {
        return null;
    }

    @Override
    public Iterator<SourceItem> getItemIterator() {
        try {
            return Files.list(dataDir).<SourceItem>map(p -> {
                if (Files.isDirectory(p)) {
                    return new SimpleDir(p);
                } else {
                    return new SimpleFile(p);
                }
            }).iterator();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void onTransferInitialized(Transfer transfer) {
        logger.info("Client transfer initialized, transferId={}", transfer.getTransferId());
        this.transfer = transfer;
    }

    @Override
    public void onTransferProgress(TransferStatus status) {
        logger.info("Client transfer progressed, transferId={}, detail: {}", transfer.getTransferId(), status);
    }

    @Override
    public void onTransferSuccess(GenericDataType response) {
        logger.info("Client transfer succeeded, transferId={}, response: {}", transfer.getTransferId(), response);
    }

    @Override
    public void onTransferCanceled() {
        logger.info("Client transfer canceled, transferId={}", transfer.getTransferId());
    }

    @Override
    public void onTransferFailed(Throwable cause) {
        logger.info("Client transfer failed, transferId=" + transfer.getTransferId(), cause);
    }
}
