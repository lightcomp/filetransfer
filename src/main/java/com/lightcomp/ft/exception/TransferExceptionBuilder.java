package com.lightcomp.ft.exception;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.lightcomp.ft.TransferInfo;
import com.lightcomp.ft.xsd.v1.ErrorCode;
import com.lightcomp.ft.xsd.v1.ErrorDescription;

import cxf.FileTransferException;

public class TransferExceptionBuilder {

    private final List<MessagePart> msgParts = new ArrayList<>();

    private Throwable cause;

    private ErrorCode errorCode;

    private TransferExceptionBuilder() {
    }

    public TransferExceptionBuilder setTransfer(TransferInfo errorInfo) {
        String transferId = errorInfo.getTransferId();
        if (transferId != null) {
            msgParts.add(new MessageParam("transferId", transferId, 1));
        }
        String requestId = errorInfo.getRequestId();
        if (requestId != null) {
            msgParts.add(new MessageParam("requestId", requestId, 2));
        }
        boolean cp = errorInfo.isCancelRequested();
        if (cp) {
            msgParts.add(new MessageParam("cancelPending", Boolean.TRUE, 4));
        }
        return this;
    }

    public TransferExceptionBuilder setCode(ErrorCode errorCode) {
        msgParts.add(new MessageParam("errorCode", errorCode, 3));
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
        msgParts.add(new MessageParam(name, value, null));
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

    public String buildMessage() {
        if (msgParts.isEmpty()) {
            return null;
        }
        Collections.sort(msgParts);

        Iterator<MessagePart> it = msgParts.iterator();
        StringBuilder sb = new StringBuilder();
        MessagePart curr = it.next();
        while (true) {
            curr.write(sb);
            if (!it.hasNext()) {
                break;
            }
            MessagePart next = it.next();
            curr.writeSeparator(sb, next);
            curr = next;
        }
        return sb.toString();
    }

    public static TransferExceptionBuilder from(String message) {
        TransferExceptionBuilder builder = new TransferExceptionBuilder();
        MessageContent content = new MessageContent(message);
        builder.msgParts.add(content);
        return builder;
    }
}
