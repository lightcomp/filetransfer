package com.lightcomp.ft;

import java.nio.file.Path;

import com.lightcomp.ft.server.TransferStatus;
import com.lightcomp.ft.server.UploadAcceptor;

public class UploadAcceptorImpl implements UploadAcceptor {

    private final String transferId;

    private final Path uploadDir;

    private final AcceptorInterceptor interceptor;

    public UploadAcceptorImpl(String transferId, Path uploadDir, AcceptorInterceptor interceptor) {
        this.transferId = transferId;
        this.uploadDir = uploadDir;
        this.interceptor = interceptor;
    }

    @Override
    public Mode getMode() {
        return Mode.UPLOAD;
    }

    @Override
    public String getTransferId() {
        return transferId;
    }

    @Override
    public Path getUploadDir() {
        return uploadDir;
    }

    @Override
    public void onTransferProgress(TransferStatus status) {
        interceptor.onTransferProgress(status);
    }

    @Override
    public void onTransferSuccess() {
        interceptor.onTransferSuccess();
    }

    @Override
    public void onTransferCanceled() {
        interceptor.onTransferCanceled();
    }

    @Override
    public void onTransferFailed(Throwable cause) {
        interceptor.onTransferFailed(cause);
    }
}
