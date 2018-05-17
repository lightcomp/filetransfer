package com.lightcomp.ft.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;

import com.lightcomp.ft.core.TransferInfo;

public class TransferExceptionBuilder {

    private final LinkedHashMap<String, Object> params = new LinkedHashMap<>();

    private final String message;

    private Throwable cause;

    public TransferExceptionBuilder(String message) {
        this.message = Validate.notEmpty(message);
    }

    public TransferExceptionBuilder(String message, TransferInfo transfer) {
        this(message);
        setTransfer(transfer);
    }

    public TransferExceptionBuilder setTransfer(TransferInfo transfer) {
        params.put("transferId", transfer.getTransferId());
        params.put("requestId", transfer.getRequestId());
        return this;
    }

    public TransferExceptionBuilder addParam(String name, Object value) {
        params.put(name, value);
        return this;
    }

    public TransferExceptionBuilder setCause(Throwable cause) {
        this.cause = cause;
        return this;
    }

    public TransferException build() {
        Map<String, Object> paramsCopy = params.isEmpty() ? null : new LinkedHashMap<>(params);
        return new TransferException(message, cause, paramsCopy);
    }

    public void log(Logger logger) {
        logger.error(buildMsg(), cause);
    }

    private String buildMsg() {
        if (params.isEmpty()) {
            return message;
        }
        StringBuilder sb = new StringBuilder(message);
        sb.append(", params=[");
        params.forEach((k, v) -> sb.append(k).append('=').append(v).append(','));
        // replace last separator with bracket
        sb.setCharAt(sb.length() - 1, ']');
        return sb.toString();
    }
}
