package com.lightcomp.ft.server.internal;

import com.lightcomp.ft.server.ErrorDesc;
import com.lightcomp.ft.server.TransferStatus;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.xsd.v1.ErrorCode;
import com.lightcomp.ft.xsd.v1.Frame;
import com.lightcomp.ft.xsd.v1.GenericDataType;

public class TerminatedTransfer implements Transfer {

    private final String transferId;

    private final TransferStatus status;

    public TerminatedTransfer(String transferId, TransferStatus status) {
        this.transferId = transferId;
        this.status = status;
    }

    @Override
    public void recvFrame(Frame frame) throws FileTransferException {
        throw createTerminatedException();
    }

    @Override
    public Frame sendFrame(int seqNum) throws FileTransferException {
        throw createTerminatedException();
    }

    @Override
    public GenericDataType finish() throws FileTransferException {
        throw createTerminatedException();
    }

    @Override
    public void abort() throws FileTransferException {
        // NOP
    }

    @Override
    public TransferStatus getConfirmedStatus() throws FileTransferException {
        return status;
    }

    private FileTransferException createTerminatedException() {
        ErrorDesc ed = status.getErrorDesc();
        if (ed != null) {
            return ServerError.createEx(ed, ErrorCode.FATAL);
        }
        return new ServerError("Transfer is terminated").addParam("transferId", transferId)
                .addParam("finalState", status.getState()).createEx();
    }
}
