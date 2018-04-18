package com.lightcomp.ft.exception;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.lightcomp.ft.core.TransferInfo;

public class TransferExceptionBuilder {

    private final List<MessageParam> params = new ArrayList<>();

    private final String message;

    private Throwable cause;

    private TransferExceptionBuilder(String message) {
        this.message = message;
    }

    public TransferExceptionBuilder setTransfer(TransferInfo transferInfo) {
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

    public TransferExceptionBuilder setCause(Throwable cause) {
        this.cause = cause;
        return this;
    }

    public TransferExceptionBuilder addParam(String name, Object value) {
        params.add(new MessageParam(name, value, null));
        return this;
    }

    public TransferException build() {
        String message = buildMessage();

        return new TransferException(message, cause);
    }

    public String buildMessage() {
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

    public static TransferExceptionBuilder from(String message) {
        return new TransferExceptionBuilder(message);
    }

    public static TransferExceptionBuilder from(TransferInfo transferInfo, String message) {
        return new TransferExceptionBuilder(message).setTransfer(transferInfo);
    }
}
