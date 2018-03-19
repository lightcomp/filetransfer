package com.lightcomp.ft.sender.impl.phase;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import org.apache.cxf.transport.http.HTTPException;

import com.lightcomp.ft.xsd.v1.ErrorCode;
import com.lightcomp.ft.xsd.v1.ErrorDescription;

import cxf.FileTransferException;

public class OperationError {

    private final int category;

    private final Throwable cause;

    private final ErrorDescription desc;

    private OperationError(Throwable cause, ErrorDescription desc) {
        this.category = resolveCategory(cause, desc);
        this.cause = cause;
        this.desc = desc;
    }

    public Throwable getCause() {
        return cause;
    }

    public ErrorDescription getDesc() {
        return desc;
    }

    /**
     * @return True when server returned FATAL code or it's unknown exception.
     */
    public boolean isFatal() {
        return category == 1;
    }

    /**
     * @return True when server returned BUSY code.
     */
    public boolean isBusy() {
        return category == 2;
    }

    /**
     * @return True when receiver was unreachable or request timed out.
     */
    public boolean isCommunication() {
        return category == 3;
    }

    public static OperationError from(FileTransferException fte) {
        return new OperationError(fte, fte.getFaultInfo());
    }

    public static OperationError from(Throwable t) {
        return new OperationError(t, null);
    }

    /**
     * @return 1 - server returned FATAL code or it's unknown exception<br>
     *         2 - server returned BUSY code<br>
     *         3 - receiver was unreachable or request timed out
     */
    private static int resolveCategory(Throwable cause, ErrorDescription desc) {
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