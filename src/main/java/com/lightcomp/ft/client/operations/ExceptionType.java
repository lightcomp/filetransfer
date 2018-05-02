package com.lightcomp.ft.client.operations;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import org.apache.cxf.transport.http.HTTPException;

import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.xsd.v1.ErrorCode;
import com.lightcomp.ft.xsd.v1.ErrorDescription;

public enum ExceptionType {
    UKNOWN, FATAL, CONNECTION, BUSY;

    public static ExceptionType resolve(Throwable t) {
        // inspect error code
        if (t instanceof FileTransferException) {
            ErrorDescription desc = ((FileTransferException) t).getFaultInfo();
            if (desc != null) {
                ErrorCode code = desc.getErrorCode();
                if (code == ErrorCode.FATAL) {
                    return ExceptionType.FATAL;
                }
                if (code == ErrorCode.BUSY) {
                    return ExceptionType.BUSY;
                }
            }
            return ExceptionType.UKNOWN;
        }
        // inspect stack trace
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
            t = t.getCause();
        }
        return ExceptionType.UKNOWN;
    }
}