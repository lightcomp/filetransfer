package com.lightcomp.ft.exception;

import java.util.LinkedHashMap;

import org.slf4j.Logger;

import com.lightcomp.ft.core.TransferInfo;

public class TransferExceptionBuilder {

    private final LinkedHashMap<String, Object> params = new LinkedHashMap<>();

    private final String message;

    private Throwable cause;

    public TransferExceptionBuilder(String message) {
        this.message = message;
    }

    public TransferExceptionBuilder setTransfer(TransferInfo transfer) {
        params.put("transferId", transfer.getTransferId());
        params.put("requestId", transfer.getRequestId());
        return this;
    }

    public TransferExceptionBuilder setCause(Throwable cause) {
        this.cause = cause;
        return this;
    }

    public TransferExceptionBuilder addParam(String name, Object value) {
        params.put(name, value);
        return this;
    }

    public TransferException build() {
        return new TransferException(message, cause, params);
    }

    public void log(Logger logger) {
        logger.error(createLogMessage(), cause);
    }

    private String createLogMessage() {
        if (params.isEmpty()) {
            return message;
        }
        StringBuilder sb = new StringBuilder(message);
        params.forEach((k, v) -> {
            sb.append(',').append(k).append('=').append(v);
        });
        return sb.toString();
    }

    public static TransferExceptionBuilder from(String message) {
        return new TransferExceptionBuilder(message);
    }

    public static TransferExceptionBuilder from(String message, TransferInfo transfer) {
        return new TransferExceptionBuilder(message).setTransfer(transfer);
    }
}
