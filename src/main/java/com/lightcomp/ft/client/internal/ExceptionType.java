package com.lightcomp.ft.client.internal;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import org.apache.cxf.transport.http.HTTPException;

import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.xsd.v1.ErrorCode;
import com.lightcomp.ft.xsd.v1.ErrorDescription;

public enum ExceptionType {
    UKNOWN, CONNECTION, BUSY, FATAL;

    public boolean isServerError() {
        return this == BUSY || this == FATAL;
    }

    public static ExceptionType resolve(Throwable t) {
        while (t != null) {
            if (t instanceof SocketTimeoutException) {
                return ExceptionType.CONNECTION; // timed out
            }
            if (t instanceof ConnectException) {
                return ExceptionType.CONNECTION; // connection refused
            }
            if (t instanceof HTTPException) {
                return ExceptionType.CONNECTION; // invalid HTTP response
            }
            if (t instanceof UnknownHostException) {
                return ExceptionType.CONNECTION; // host not found
            }
            if (t instanceof FileTransferException) {
                ErrorDescription desc = ((FileTransferException) t).getFaultInfo();
                if (desc != null) {
                    ErrorCode code = desc.getErrorCode();
                    if (code == ErrorCode.FATAL) {
                        return ExceptionType.FATAL; // server fatal
                    }
                    if (code == ErrorCode.BUSY) {
                        return ExceptionType.BUSY; // server busy
                    }
                }
            }
            // inspect next cause
            t = t.getCause();
        }
        return ExceptionType.UKNOWN;
    }
}