package com.lightcomp.ft.simple;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.core.send.items.DirReader;
import com.lightcomp.ft.core.send.items.SourceItemReader;
import com.lightcomp.ft.server.DownloadHandler;
import com.lightcomp.ft.server.ErrorDesc;
import com.lightcomp.ft.server.TransferStatus;
import com.lightcomp.ft.xsd.v1.GenericDataType;

public class DwnldHandlerImpl implements DownloadHandler {

    private static final Logger logger = LoggerFactory.getLogger(DwnldHandlerImpl.class);

    private final String transferId;

    private final GenericDataType response;

    private final Path dataDir;

    public DwnldHandlerImpl(String transferId, GenericDataType response, Path dataDir) {
        this.transferId = transferId;
        this.response = response;
        this.dataDir = dataDir;
    }

    @Override
    public Mode getMode() {
        return Mode.DOWNLOAD;
    }

    @Override
    public String getRequestId() {
        return null;
    }
    
    @Override
    public SourceItemReader getRootItemsReader() {
    	return new DirReader(dataDir);
    }

    @Override
    public void onTransferProgress(TransferStatus status) {
        logger.info("Server transfer progressed, transferId={}, detail: {}", transferId, status);
    }
    
    @Override
    public GenericDataType finishTransfer() {
        logger.info("Server transfer finished, transferId={}", transferId);
        return response;
    }

    @Override
    public void onTransferCanceled() {
        logger.info("Server transfer canceled, transferId={}", transferId);
    }

    @Override
    public void onTransferFailed(ErrorDesc errorDesc) {
        logger.info("Server transfer failed, transferId={}, desc: {}", transferId, errorDesc);
    }
}
