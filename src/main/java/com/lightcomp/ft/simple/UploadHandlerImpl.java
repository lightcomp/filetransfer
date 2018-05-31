package com.lightcomp.ft.simple;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lightcomp.ft.server.ErrorDesc;
import com.lightcomp.ft.server.TransferStatus;
import com.lightcomp.ft.server.UploadHandler;
import com.lightcomp.ft.xsd.v1.GenericDataType;

public class UploadHandlerImpl implements UploadHandler {

    private static final Logger logger = LoggerFactory.getLogger(UploadHandlerImpl.class);

    private final String transferId;

    private final GenericDataType response;

    private final Path uploadDir;

    public UploadHandlerImpl(String transferId, GenericDataType response, Path uploadDir) {
        this.transferId = transferId;
        this.response = response;
        this.uploadDir = uploadDir;
    }

    @Override
    public Mode getMode() {
        return Mode.UPLOAD;
    }

    @Override
    public String getRequestId() {
        return null;
    }

    @Override
    public Path getUploadDir() {
        return uploadDir;
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
