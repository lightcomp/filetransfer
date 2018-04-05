package com.lightcomp.ft.client.internal.operations;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import org.apache.cxf.transport.http.HTTPException;

import com.lightcomp.ft.xsd.v1.ErrorCode;
import com.lightcomp.ft.xsd.v1.ErrorDescription;

import cxf.FileTransferException;

class OperationError {

    private final Operation source;

    private final Throwable cause;

    private final int type;

    private OperationError(Operation source, Throwable cause, int type) {
        this.source = source;
        this.cause = cause;
        this.type = type;
    }

    public Operation getSource() {
        return source;
    }

    public Throwable getCause() {
        return cause;
    }

    /**
     * @return True when server returned FATAL code or it's unknown exception.
     */
    public boolean isFatal() {
        return type == 1;
    }

    /**
     * @return True when server returned BUSY code.
     */
    public boolean isBusy() {
        return type == 2;
    }

    /**
     * @return True when receiver was unreachable or request timed out.
     */
    public boolean isCommunication() {
        return type == 3;
    }

    public static OperationError from(Operation source, FileTransferException fte) {
        ErrorDescription desc = fte.getFaultInfo();
        int type = resolveErrorType(fte, desc);
        return new OperationError(source, fte, type);
    }

    public static OperationError from(Operation source, Throwable t) {
        int type = resolveErrorType(t, null);
        return new OperationError(source, t, type);
    }

    /**
     * @return 1 - server returned FATAL code or it's unknown exception<br>
     *         2 - server returned BUSY code<br>
     *         3 - receiver was unreachable or request timed out
     */
    private static int resolveErrorType(Throwable cause, ErrorDescription desc) {
        if (desc != null) {
            ErrorCode code = desc.getErrorCode();
            if (code == ErrorCode.BUSY) {
                return 2; // busy
            }
            return 1; // fatal
        }
        // inspect causes
        while (cause != null) {
            if (cause instanceof SocketTimeoutException) {
                return 3; // communication - timed out
            }
            if (cause instanceof ConnectException) {
                return 3; // communication - connection refused
            }
            if (cause instanceof HTTPException) {
                return 3; // communication - invalid HTTP response
            }
            if (cause instanceof UnknownHostException) {
                return 3; // communication - host not found
            }
            cause = cause.getCause();
        }
        return 1; // fatal
    }
}