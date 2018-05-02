package com.lightcomp.ft.exception;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;

import com.lightcomp.ft.core.TransferInfo;

public class TransferExceptionBuilder {

    private final List<MessageParam> params = new ArrayList<>();

    private final String message;

    private Throwable cause;

    private TransferExceptionBuilder(String message) {
        this.message = message;
    }

    public TransferExceptionBuilder setTransfer(TransferInfo transfer) {
        params.add(new MessageParam("transferId", transfer.getTransferId(), 1));
        params.add(new MessageParam("requestId", transfer.getRequestId(), 2));
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
        return new TransferException(buildMsg(), cause);
    }

    public void log(Logger logger) {
        logger.error(buildMsg(), cause);
    }

    public String buildMsg() {
        if (params.isEmpty()) {
            return message;
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

    public static TransferExceptionBuilder from(String message, TransferInfo transfer) {
        return new TransferExceptionBuilder(message).setTransfer(transfer);
    }
}
