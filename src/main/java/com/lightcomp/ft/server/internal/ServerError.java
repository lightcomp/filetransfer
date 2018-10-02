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

public class ServerError {

    private final Map<String, Object> params = new LinkedHashMap<>();

    private final String message;

    private Throwable cause;

    private ErrorDescImpl desc;

    private ErrorCode code = ErrorCode.FATAL;

    public ServerError(String message) {
        this.message = Validate.notBlank(message);
    }

    public ServerError(String message, TransferInfo transfer) {
        this(message);
        setTransfer(transfer);
    }

    public boolean isFatal() {
        return code == ErrorCode.FATAL;
    }

    public ServerError setTransfer(TransferInfo transfer) {
        params.put("transferId", transfer.getTransferId());
        params.put("requestId", transfer.getRequestId());
        resetDesc();
        return this;
    }

    public ServerError addParam(String name, Object value) {
        params.put(name, value);
        resetDesc();
        return this;
    }

    public ServerError setCause(Throwable cause) {
        this.cause = cause;
        resetDesc();
        return this;
    }

    public ServerError setCode(ErrorCode code) {
        this.code = Validate.notNull(code);
        resetDesc();
        return this;
    }

    private void resetDesc() {
        desc = null;
    }

    public ErrorDesc getDesc() {
        if (desc == null) {
            desc = createDesc();
        }
        return desc;
    }

    private ErrorDescImpl createDesc() {
        Map<String, Object> paramsCopy = new LinkedHashMap<>(params);
        ErrorDescImpl desc = new ErrorDescImpl(message);
        desc.setParams(paramsCopy);
        // fill detail with cause if present
        if (cause != null) {
            desc.setDetail(cause.getMessage());
            desc.setStackTrace(cause.getStackTrace());
            // copy missing attributes if it's transfer exception
            if (cause instanceof TransferException) {
                Map<String, Object> causeParams = ((TransferException) cause).getParams();
                if (causeParams != null) {
                    causeParams.forEach((n, v) -> paramsCopy.putIfAbsent("cause." + n, v));
                }
            }
        }
        return desc;
    }

    public FileTransferException createEx() {
        return createEx(getDesc(), code);
    }

    public void log(Logger logger) {
        StringBuilder sb = new StringBuilder();
        getDesc().appendTo(sb, false);
        logger.error(sb.toString(), cause);
    }

    public void log(Logger logger, String leadingMsg) {
        StringBuilder sb = new StringBuilder(leadingMsg);
        getDesc().appendTo(sb, false);
        logger.error(sb.toString(), cause);
    }

    /**
     * Builds exception from error description.
     */
    public static FileTransferException createEx(ErrorDesc errorDesc, ErrorCode errorCode) {
        StringBuilder sb = new StringBuilder();
        errorDesc.appendTo(sb, true);

        ErrorDescription desc = new ErrorDescription();
        desc.setDetail(sb.toString());
        desc.setErrorCode(errorCode);

        return new FileTransferException(errorDesc.getMessage(), desc);
    }
}
