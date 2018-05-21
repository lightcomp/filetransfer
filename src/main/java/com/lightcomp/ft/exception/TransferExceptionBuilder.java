package com.lightcomp.ft.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
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
        this.message = Validate.notBlank(message);
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

    /**
     * Logs builder message, params and cause. If the cause is know implementation his detail is also logged.
     */
    public void log(Logger logger) {
        StringBuilder sb = new StringBuilder(message);
        appendParams(sb, params, "params");
        if (cause instanceof FileTransferException) {
            if (appendServerErrorDetail(sb, (FileTransferException) cause)) {
                logger.error(sb.toString());
                return;
            }
        } else if (cause instanceof TransferException) {
            Map<String, Object> params = ((TransferException) cause).getParams();
            appendParams(sb, params, "causeParams");
        }
        logger.error(sb.toString(), cause);
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

    private static boolean appendServerErrorDetail(StringBuilder sb, FileTransferException fte) {
        ErrorDescription desc = fte.getFaultInfo();
        if (desc == null) {
            return false;
        }
        String detail = StringUtils.trimToNull(desc.getDetail());
        if (detail == null) {
            return false;
        }
        if (sb.length() > 0) {
            sb.append(System.lineSeparator());
        }
        sb.append("Server error detail: ").append(detail);
        return true;
    }
}
