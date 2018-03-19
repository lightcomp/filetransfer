package com.lightcomp.ft.exception;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.lightcomp.ft.TransferInfo;
import com.lightcomp.ft.xsd.v1.ErrorCode;
import com.lightcomp.ft.xsd.v1.ErrorDescription;

import cxf.FileTransferException;

public class TransferExceptionBuilder {

    private final List<MessagePart> messageParts = new ArrayList<>();

    private Throwable cause;

    private ErrorCode errorCode;

    private TransferExceptionBuilder() {
    }

    public TransferExceptionBuilder setTransfer(TransferInfo errorInfo) {
        String transferId = errorInfo.getTransferId();
        if (transferId != null) {
            messageParts.add(new MessageParam("transferId", transferId, 1));
        }
        String requestId = errorInfo.getRequestId();
        if (requestId != null) {
            messageParts.add(new MessageParam("requestId", requestId, 2));
        }
        boolean canceled = errorInfo.isCanceled();
        if (canceled) {
            messageParts.add(new MessageParam("canceled", Boolean.TRUE, 4));
        }
        return this;
    }

    public TransferExceptionBuilder setCode(ErrorCode errorCode) {
        messageParts.add(new MessageParam("errorCode", errorCode, 3));
        this.errorCode = errorCode;
        return this;
    }

    public TransferExceptionBuilder setCause(FileTransferException cause) {
        ErrorDescription desc = cause.getFaultInfo();
        if (desc != null) {
            setCode(desc.getErrorCode());
        }
        this.cause = cause;
        return this;
    }

    public TransferExceptionBuilder setCause(Throwable cause) {
        if (cause instanceof FileTransferException) {
            return setCause((FileTransferException) cause);
        }
        this.cause = cause;
        return this;
    }

    public TransferExceptionBuilder addParam(String name, Object value) {
        messageParts.add(new MessageParam(name, value, null));
        return this;
    }

    public TransferException build() {
        String message = buildMessage();

        return new TransferException(message, cause);
    }

    public FileTransferException buildFault() {
        String message = buildMessage();

        ErrorDescription errorDesc = new ErrorDescription();
        errorDesc.setErrorCode(errorCode);

        return new FileTransferException(message, errorDesc, cause);
    }

    private String buildMessage() {
        StringBuilder sb = new StringBuilder();

        Collections.sort(messageParts);
        messageParts.forEach(mp -> mp.write(sb));

        return sb.toString();
    }

    public static TransferExceptionBuilder from(String message) {
        TransferExceptionBuilder builder = new TransferExceptionBuilder();
        MessageContent content = new MessageContent(message);
        builder.messageParts.add(content);
        return builder;
    }
}
