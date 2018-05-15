package com.lightcomp.ft.exception;

import java.util.Collections;
import java.util.Map;

public class TransferException extends Exception {

    private static final long serialVersionUID = 1L;

    protected final Map<String, Object> params;

    public TransferException(String message) {
        this(message, null, null);
    }

    public TransferException(String message, Throwable cause) {
        this(message, cause, null);
    }

    public TransferException(String message, Throwable cause, Map<String, Object> params) {
        super(message, cause);
        this.params = params;
    }

    public Map<String, Object> getParams() {
        if (params == null) {
            return null;
        }
        return Collections.unmodifiableMap(params);
    }
}
