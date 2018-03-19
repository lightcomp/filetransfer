package com.lightcomp.ft.exception;

public class TransferException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TransferException(String message) {
        super(message);
    }

    public TransferException(String message, Throwable cause) {
        super(message, cause);
    }
}
