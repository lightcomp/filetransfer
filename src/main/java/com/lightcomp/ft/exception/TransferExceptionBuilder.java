package com.lightcomp.ft.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;

import com.lightcomp.ft.core.TransferInfo;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.xsd.v1.ErrorDescription;

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

    public TransferExceptionBuilder addParams(Map<String, Object> params) {
        this.params.putAll(params);
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
        StringBuilder sb = new StringBuilder(message);
        // append parameters
        if (params.size() > 0) {
            sb.append(", params=[");
            params.forEach((k, v) -> sb.append(k).append('=').append(v).append(','));
            // replace last separator with bracket
            sb.setCharAt(sb.length() - 1, ']');
        }
        // log server detail if present
        if (cause instanceof FileTransferException) {
            ErrorDescription desc = ((FileTransferException) cause).getFaultInfo();
            if (desc != null) {
                // server description found
                sb.append(System.lineSeparator()).append("Server description: ").append(desc.getDetail());
                if (!logger.isDebugEnabled()) {
                    // do not log stack trace
                    logger.error(sb.toString());
                    return;
                }
            }
        }
        logger.error(sb.toString(), cause);
    }
}
