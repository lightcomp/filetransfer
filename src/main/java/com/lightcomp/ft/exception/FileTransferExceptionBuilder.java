package com.lightcomp.ft.exception;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.lightcomp.ft.core.TransferInfo;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.xsd.v1.ErrorCode;
import com.lightcomp.ft.xsd.v1.ErrorDescription;

public class FileTransferExceptionBuilder {

    private final List<MessageParam> params = new ArrayList<>();

    private final String message;

    private Throwable cause;

    private ErrorCode errorCode = ErrorCode.FATAL;

    private FileTransferExceptionBuilder(String message) {
        this.message = message;
    }

    public FileTransferExceptionBuilder setTransfer(TransferInfo transfer) {
        params.add(new MessageParam("transferId", transfer.getTransferId(), 1));
        params.add(new MessageParam("requestId", transfer.getRequestId(), 2));
        return this;
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
        String msg = buildMsg();

        ErrorDescription errorDesc = new ErrorDescription();
        errorDesc.setErrorCode(errorCode);
        errorDesc.setDetail(msg);

        return new FileTransferException(msg, errorDesc, cause);
    }

    private String buildMsg() {
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

    public static FileTransferExceptionBuilder from(String message) {
        return new FileTransferExceptionBuilder(message);
    }

    public static FileTransferExceptionBuilder from(String message, TransferInfo transfer) {
        return new FileTransferExceptionBuilder(message).setTransfer(transfer);
    }
}
