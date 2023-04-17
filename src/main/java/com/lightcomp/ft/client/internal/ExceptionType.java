package com.lightcomp.ft.client.internal;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import org.apache.cxf.transport.http.HTTPException;

import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.xsd.v1.ErrorCode;
import com.lightcomp.ft.xsd.v1.ErrorDescription;

import jakarta.xml.bind.MarshalException;

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
        if (cause instanceof SocketException) {
            // general socket exception
            // e.g.: Caused by: java.net.SocketException: Connection reset
            // at java.net.SocketInputStream.read(SocketInputStream.java:210)
            // at java.net.SocketInputStream.read(SocketInputStream.java:141)
            // at java.io.BufferedInputStream.fill(BufferedInputStream.java:246)
            // at java.io.BufferedInputStream.read1(BufferedInputStream.java:286)
            // at java.io.BufferedInputStream.read(BufferedInputStream.java:345)
            // at sun.net.www.http.HttpClient.parseHTTPHeader(HttpClient.java:704)
            // at sun.net.www.http.HttpClient.parseHTTP(HttpClient.java:647)
            // ....
            return ExceptionType.CONNECTION;
        }
        if (cause instanceof SocketTimeoutException) {
            return ExceptionType.CONNECTION; // timed out
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