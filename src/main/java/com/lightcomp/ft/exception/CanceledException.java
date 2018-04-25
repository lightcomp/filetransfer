package com.lightcomp.ft.exception;

public class CanceledException extends Exception {

    private static final long serialVersionUID = 1L;

    @Override
    public String getMessage() {
        return "Transfer was canceled";
    }
}
