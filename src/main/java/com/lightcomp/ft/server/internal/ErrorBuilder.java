package com.lightcomp.ft.server.internal;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;

import com.lightcomp.ft.core.TransferInfo;
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.server.ErrorDesc;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.xsd.v1.ErrorCode;
import com.lightcomp.ft.xsd.v1.ErrorDescription;

public class ErrorBuilder {

    private final Map<String, Object> params = new LinkedHashMap<>();

    private final String message;

    private Throwable cause;

    private ErrorDescImpl desc;

    public ErrorBuilder(String message) {
        this.message = Validate.notEmpty(message);
    }

    public ErrorBuilder(String message, TransferInfo transfer) {
        this(message);
        setTransfer(transfer);
    }

    public ErrorBuilder setTransfer(TransferInfo transfer) {
        params.put("transferId", transfer.getTransferId());
        params.put("requestId", transfer.getRequestId());
        resetDesc();
        return this;
    }

    public ErrorBuilder addParam(String name, Object value) {
        params.put(name, value);
        resetDesc();
        return this;
    }

    public ErrorBuilder setCause(Throwable cause) {
        this.cause = cause;
        resetDesc();
        return this;
    }

    public ErrorDesc buildDesc() {
        if (desc != null) {
            return desc;
        }
        desc = new ErrorDescImpl(message);
        // copy builder parameters
        Map<String, Object> allParams = new LinkedHashMap<>(params);
        // prepare cause
        if (cause != null) {
            desc.setDetail(cause.getMessage());
            desc.setStackTrace(cause.getStackTrace());
            // copy missing parameters
            if (cause instanceof TransferException) {
                TransferException te = (TransferException) cause;
                Map<String, Object> teParams = te.getParams();
                if (teParams != null) {
                    teParams.forEach(allParams::putIfAbsent);
                }
            }
        }
        // keep null if params are empty
        if (allParams.size() > 0) {
            desc.setParams(allParams);
        }
        return desc;
    }

    public FileTransferException buildEx() {
        return buildEx(ErrorCode.FATAL);
    }

    public FileTransferException buildEx(ErrorCode errorCode) {
        return buildEx(buildDesc(), errorCode);
    }

    /**
     * Logs error builder state with trace of exception (if present).
     *
     * @param logger
     *            not-null
     */
    public void log(Logger logger) {
        StringBuilder sb = new StringBuilder();
        append(buildDesc(), sb);
        logger.error(sb.toString(), cause);
    }

    /**
     * Logs error builder state and trace of exception (if present).
     * 
     * @param logger
     *            not-null
     * @param message
     *            custom message will be logged first, not-null
     */
    public void log(Logger logger, String message) {
        StringBuilder sb = new StringBuilder(message);
        append(buildDesc(), sb);
        logger.error(sb.toString(), cause);
    }

    private void resetDesc() {
        desc = null;
    }

    private static void append(ErrorDesc desc, StringBuilder sb) {
        // append description
        if (sb.length() > 0) {
            sb.append(", ");
        }
        sb.append("errorDesc=").append(desc.getMessage());
        // append detail
        String detail = desc.getDetail();
        if (detail != null) {
            sb.append(", errorDetail=").append(detail);
        }
        // append parameters
        Map<String, Object> params = desc.getParams();
        if (params != null && params.size() > 0) {
            sb.append(", errorParams=[");
            params.forEach((n, v) -> sb.append(n).append('=').append(v).append(','));
            // replace last separator with bracket
            sb.setCharAt(sb.length() - 1, ']');
        }
    }

    /**
     * Builds exception from error description, stack trace is not used.
     */
    public static FileTransferException buildEx(ErrorDesc errorDesc, ErrorCode errorCode) {
        StringBuilder sb = new StringBuilder();
        append(errorDesc, sb);

        ErrorDescription desc = new ErrorDescription();
        desc.setDetail(sb.toString());
        desc.setErrorCode(errorCode);

        return new FileTransferException(errorDesc.getMessage(), desc);
    }
}
