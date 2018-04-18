package com.lightcomp.ft.client.internal.operations;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import org.apache.cxf.transport.http.HTTPException;

import com.lightcomp.ft.core.TransferInfo;
import com.lightcomp.ft.exception.CanceledException;
import com.lightcomp.ft.exception.TransferException;
import com.lightcomp.ft.xsd.v1.ErrorCode;
import com.lightcomp.ft.xsd.v1.ErrorDescription;
import com.lightcomp.ft.xsd.v1.FileTransferStatus;

import cxf.FileTransferException;
import cxf.FileTransferService;

public abstract class RecoverableOperation {

    public enum ExceptionType {
        UKNOWN, FATAL, CONNECTION, BUSY
    }

    protected final TransferInfo transferInfo;

    private final RecoveryHandler handler;

    private boolean recovery;

    protected RecoverableOperation(TransferInfo transferInfo, RecoveryHandler handler) {
        this.transferInfo = transferInfo;
        this.handler = handler;
    }

    public void execute(FileTransferService service) throws CanceledException {
        do {
            try {
                executeInternal(service);
                recovery = false;
            } catch (Throwable t) {
                if (!isRecoverable(t)) {
                    throw createException(t);
                }
                recovery = true;
                handler.waitBeforeRecovery();
            }
        } while (recovery);
    }

    protected abstract void send(FileTransferService service) throws FileTransferException;

    protected abstract TransferException createException(Throwable cause);

    protected abstract boolean isFinished(FileTransferStatus status);

    protected boolean isRecoverable(Throwable t) {
        ExceptionType type = resolveExceptionType(t);
        if (type == ExceptionType.CONNECTION || type == ExceptionType.BUSY) {
            return true;
        }
        return false;
    }

    private void executeInternal(FileTransferService service) throws FileTransferException {
        if (recovery) {
            // try to get current server status
            FileTransferStatus status = service.status(transferInfo.getTransferId());
            // if succeeded test server status
            if (isFinished(status)) {
                return;
            }
        }
        send(service);
    }

    public static ExceptionType resolveExceptionType(Throwable t) {
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
