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

public class ErrorContext {

    private final Map<String, Object> params = new LinkedHashMap<>();

    private final String message;

    private Throwable cause;

    private ErrorDescImpl desc;

    private ErrorCode code = ErrorCode.FATAL;

    public ErrorContext(String message) {
        this.message = Validate.notBlank(message);
    }

    public ErrorContext(String message, TransferInfo transfer) {
        this(message);
        setTransfer(transfer);
    }

    public ErrorContext setTransfer(TransferInfo transfer) {
        params.put("transferId", transfer.getTransferId());
        params.put("requestId", transfer.getRequestId());
        resetDesc();
        return this;
    }

    public ErrorContext addParam(String name, Object value) {
        params.put(name, value);
        resetDesc();
        return this;
    }

    public ErrorContext setCause(Throwable cause) {
        this.cause = cause;
        resetDesc();
        return this;
    }

    public ErrorContext setCode(ErrorCode code) {
        this.code = Validate.notNull(code);
        resetDesc();
        return this;
    }

    private void resetDesc() {
        desc = null;
    }

    public boolean isFatal() {
        return code == ErrorCode.FATAL;
    }

    public ErrorDesc getDesc() {
        if (desc == null) {
            desc = createDesc();
        }
        return desc;
    }

    private ErrorDescImpl createDesc() {
        Map<String, Object> cpyMap = new LinkedHashMap<>(params);
        ErrorDescImpl ed = new ErrorDescImpl(message);
        ed.setParams(cpyMap);
        if (cause != null) {
            ed.setDetail(cause.getMessage());
            ed.setStackTrace(cause.getStackTrace());
            // copy missing attributes
            if (cause instanceof TransferException) {
                Map<String, Object> causeMap = ((TransferException) cause).getParams();
                if (causeMap != null) {
                    causeMap.forEach((n, v) -> cpyMap.putIfAbsent("detail." + n, v));
                }
            }
        }
        return ed;
    }

    public FileTransferException createEx() {
        return createEx(getDesc(), code);
    }

    /**
     * Logs error builder state with trace of exception (if present).
     *
     * @param logger
     *            not-null
     */
    public void log(Logger logger) {
        StringBuilder sb = new StringBuilder();
        appendErrorDesc(getDesc(), sb);
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
        appendErrorDesc(getDesc(), sb);
        logger.error(sb.toString(), cause);
    }

    private static void appendErrorDesc(ErrorDesc desc, StringBuilder sb) {
        // append description
        if (sb.length() > 0) {
            sb.append(", errorDesc=");
        }
        sb.append(desc.getMessage());
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
    public static FileTransferException createEx(ErrorDesc errorDesc, ErrorCode errorCode) {
        StringBuilder sb = new StringBuilder();
        appendErrorDesc(errorDesc, sb);

        ErrorDescription desc = new ErrorDescription();
        desc.setDetail(sb.toString());
        desc.setErrorCode(errorCode);

        return new FileTransferException(errorDesc.getMessage(), desc);
    }
}
