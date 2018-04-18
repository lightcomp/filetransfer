package com.lightcomp.ft.exception;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.lightcomp.ft.core.TransferInfo;
import com.lightcomp.ft.xsd.v1.ErrorCode;
import com.lightcomp.ft.xsd.v1.ErrorDescription;

import cxf.FileTransferException;

public class FileTransferExceptionBuilder {

    private final List<MessageParam> params = new ArrayList<>();

    private final String message;

    private Throwable cause;

    private ErrorCode errorCode = ErrorCode.FATAL;

    private FileTransferExceptionBuilder(String message) {
        this.message = message;
    }

    public FileTransferExceptionBuilder setCause(Throwable cause) {
        this.cause = cause;
        return this;
    }

    public FileTransferExceptionBuilder setCode(ErrorCode errorCode) {
        this.errorCode = errorCode;
        return this;
    }

    public FileTransferExceptionBuilder addParam(String name, Object value) {
        params.add(new MessageParam(name, value, null));
        return this;
    }

    public FileTransferException build() {
        String message = buildMessage();

        ErrorDescription errorDesc = new ErrorDescription();
        errorDesc.setErrorCode(errorCode);
        errorDesc.setDetail(message);

        return new FileTransferException(message, errorDesc, cause);
    }

    private String buildMessage() {
        if (params.isEmpty()) {
            return null;
        }
        Collections.sort(params);

        StringBuilder sb = new StringBuilder(message);
        for (MessageParam param : params) {
            param.write(sb);
        }
        return sb.toString();
    }

    private FileTransferExceptionBuilder setTransfer(TransferInfo transferInfo) {
        String transferId = transferInfo.getTransferId();
        if (transferId != null) {
            params.add(new MessageParam("transferId", transferId, 1));
        }
        String requestId = transferInfo.getRequestId();
        if (requestId != null) {
            params.add(new MessageParam("requestId", requestId, 2));
        }
        return this;
    }

    public static FileTransferExceptionBuilder from(String message) {
        return new FileTransferExceptionBuilder(message);
    }

    public static FileTransferExceptionBuilder from(TransferInfo transferInfo, String message) {
        return new FileTransferExceptionBuilder(message).setTransfer(transferInfo);
    }
}
