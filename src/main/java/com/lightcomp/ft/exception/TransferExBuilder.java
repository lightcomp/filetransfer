package com.lightcomp.ft.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;

import com.lightcomp.ft.core.TransferInfo;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.xsd.v1.ErrorDescription;

public class TransferExBuilder {

    private final LinkedHashMap<String, Object> params = new LinkedHashMap<>();

    private final String message;

    private Throwable cause;

    public TransferExBuilder(String message) {
        this.message = Validate.notBlank(message);
    }

    public TransferExBuilder(String message, TransferInfo transfer) {
        this(message);
        setTransfer(transfer);
    }

    public TransferExBuilder setTransfer(TransferInfo transfer) {
        params.put("transferId", transfer.getTransferId());
        params.put("requestId", transfer.getRequestId());
        return this;
    }

    public TransferExBuilder addParam(String name, Object value) {
        params.put(name, value);
        return this;
    }

    public TransferExBuilder addParams(Map<String, Object> params) {
        this.params.putAll(params);
        return this;
    }

    public TransferExBuilder setCause(Throwable cause) {
        this.cause = cause;
        return this;
    }

    public TransferException build() {
        Map<String, Object> paramsCopy = params.isEmpty() ? null : new LinkedHashMap<>(params);
        return new TransferException(message, cause, paramsCopy);
    }

    public void log(Logger logger) {
        StringBuilder sb = new StringBuilder(message);
        appendParams(sb, params, "params");
        if (cause instanceof FileTransferException) {
            appendServerErrorDetail(sb, (FileTransferException) cause);
            logger.error(sb.toString()); // do not log stack trace for server exception
            return;
        }
        if (cause instanceof TransferException) {
            Map<String, Object> causeParams = ((TransferException) cause).getParams();
            appendParams(sb, causeParams, "causeParams");
        }
        logger.error(sb.toString(), cause);
    }

    private static void appendServerErrorDetail(StringBuilder sb, FileTransferException fte) {
        ErrorDescription desc = fte.getFaultInfo();
        if (desc == null) {
            return;
        }
        String detail = StringUtils.trimToNull(desc.getDetail());
        if (detail == null) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(System.lineSeparator());
        }
        sb.append("Server error detail: ").append(detail);
    }

    private static void appendParams(StringBuilder sb, Map<String, Object> params, String prefix) {
        if (params == null || params.isEmpty()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(", ");
        }
        sb.append(prefix).append("=[");
        params.forEach((k, v) -> sb.append(k).append('=').append(v).append(','));
        // replace last separator with bracket
        sb.setCharAt(sb.length() - 1, ']');
    }
}
