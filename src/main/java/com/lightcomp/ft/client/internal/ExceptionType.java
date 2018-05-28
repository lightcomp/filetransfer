package com.lightcomp.ft.client.internal;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import javax.xml.bind.MarshalException;

import org.apache.cxf.transport.http.HTTPException;

import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.xsd.v1.ErrorCode;
import com.lightcomp.ft.xsd.v1.ErrorDescription;

public enum ExceptionType {
    UKNOWN, CONNECTION, BUSY, FATAL;

    public static ExceptionType resolve(Throwable t) {
        while (t != null) {
            ExceptionType type = resolveCause(t);
            if (type != null) {
                return type;
            }
            t = t.getCause();
        }
        return ExceptionType.UKNOWN;
    }

    private static ExceptionType resolveCause(Throwable cause) {
        if (cause instanceof FileTransferException) {
            ErrorDescription errorDesc = ((FileTransferException) cause).getFaultInfo();
            if (errorDesc != null) {
                ErrorCode code = errorDesc.getErrorCode();
                if (code == ErrorCode.FATAL) {
                    return ExceptionType.FATAL; // server fatal
                }
                if (code == ErrorCode.BUSY) {
                    return ExceptionType.BUSY; // server busy
                }
            }
            return null; // cause unknown
        }
        // SOAPFaultException and MarshallException - happen during disconnect and MTOM transfer
        if (cause instanceof MarshalException) {
            Throwable linkedEx = ((MarshalException) cause).getLinkedException();
            if (linkedEx instanceof IOException) {
                return ExceptionType.CONNECTION; // probably fail to read/write using MTOM
            }
            return null; // cause unknown
        }
        if (cause instanceof SocketTimeoutException) {
            return ExceptionType.CONNECTION; // timed out
        }
        if (cause instanceof ConnectException) {
            return ExceptionType.CONNECTION; // connection refused
        }
        if (cause instanceof HTTPException) {
            return ExceptionType.CONNECTION; // invalid HTTP response
        }
        if (cause instanceof UnknownHostException) {
            return ExceptionType.CONNECTION; // host not found
        }
        return null; // cause unknown
    }
}