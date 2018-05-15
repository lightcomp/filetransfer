package com.lightcomp.ft.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;

import com.lightcomp.ft.core.TransferInfo;
import com.lightcomp.ft.server.ErrorDesc;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.xsd.v1.ErrorCode;

public class ErrorBuilder {

    private final Map<String, Object> params = new LinkedHashMap<>();

    private final String message;

    private Throwable cause;

    public ErrorBuilder(String message) {
        this.message = Validate.notBlank(message);
    }

    public ErrorBuilder setTransfer(TransferInfo transfer) {
        params.put("transferId", transfer.getTransferId());
        params.put("requestId", transfer.getRequestId());
        return this;
    }

    public ErrorBuilder addParam(String name, Object value) {
        params.put(name, value);
        return this;
    }

    public ErrorBuilder setCause(Throwable cause) {
        this.cause = cause;
        return this;
    }

    public ErrorDesc buildDesc() {
        Map<String, Object> params = new LinkedHashMap<>(this.params);

        ErrorDescImpl errorDesc = new ErrorDescImpl(message);
        if (cause != null) {
            errorDesc.setDetail(cause.getMessage());
            errorDesc.setStackTrace(cause.getStackTrace());
            // copy missing parameters
            if (cause instanceof TransferException) {
                Map<String, Object> causeParams = ((TransferException) cause).getParams();
                if (causeParams != null) {
                    causeParams.forEach((n, v) -> {
                        params.putIfAbsent(n, v);
                    });
                }
            }
        }
        if (!params.isEmpty()) {
            errorDesc.setParams(params);
        }
        return errorDesc;
    }

    public FileTransferException buildApiEx() {
        return buildApiEx(ErrorCode.FATAL);
    }

    public FileTransferException buildApiEx(ErrorCode code) {
        ErrorDesc errorDesc = buildDesc();
        return buildApiEx(errorDesc, code);
    }

    public TransferException buildEx() {
        // TODO Auto-generated method stub
        return null;
    }

    public void log(Logger logger) {
        // TODO Auto-generated method stub
    }

    public void log(Logger logger, String message) {
        // TODO Auto-generated method stub
    }

    public boolean isEqualDesc(ErrorDesc desc) {
        // TODO Auto-generated method stub
        return false;
    }

    public static FileTransferException buildApiEx(ErrorDesc desc, ErrorCode code) {
        // TODO Auto-generated method stub
        return null;
    }

    /*
     * private final LinkedHashMap<String, Object> params = new LinkedHashMap<>();
     * 
     * private final String message;
     * 
     * private ErrorCode code = ErrorCode.FATAL;
     * 
     * private FTExceptionBuilder(String message) { this.message = message; }
     * 
     * public String getMessage() { return message; }
     * 
     * public Map<String, Object> getParams() { return Collections.unmodifiableMap(params); }
     * 
     * public FTExceptionBuilder setTransfer(TransferInfo transfer) { params.put("transferId", transfer.getTransferId());
     * params.put("requestId", transfer.getRequestId()); return this; }
     * 
     * public FTExceptionBuilder setCode(ErrorCode code) { this.code = code; return this; }
     * 
     * public FTExceptionBuilder addParam(String name, Object value) { params.put(name, value); return this; }
     * 
     * public TransferErrorDesc getErrorDesc() { LinkedHashMap<String, Object> paramsCopy = new LinkedHashMap<>(params); return
     * new TransferErrorDesc(message, paramsCopy, null); }
     * 
     * public FileTransferException build() { String msg = buildMessage();
     * 
     * ErrorDescription errorDesc = new ErrorDescription(); errorDesc.setErrorCode(code); errorDesc.setDetail(msg);
     * 
     * return new FileTransferException(msg, errorDesc, null); }
     * 
     * private String buildMessage() { if (params.isEmpty()) { return message; } StringBuilder sb = new StringBuilder(message);
     * params.forEach((k, v) -> { sb.append(',').append(k).append('=').append(v); }); return sb.toString(); }
     * 
     * public static FTExceptionBuilder from(String message) { return new FTExceptionBuilder(message); }
     * 
     * public static FTExceptionBuilder from(String message, TransferInfo transfer) { return new
     * FTExceptionBuilder(message).setTransfer(transfer); }
     * 
     * public static FTExceptionBuilder from(TransferException te) { FTExceptionBuilder fteb = new
     * FTExceptionBuilder(te.getMessage()); fteb.params.putAll(te.getParams()); return fteb; }
     */
}
