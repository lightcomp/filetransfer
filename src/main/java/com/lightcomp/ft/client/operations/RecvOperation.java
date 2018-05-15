package com.lightcomp.ft.client.operations;

import java.math.BigInteger;

import com.lightcomp.ft.exception.TransferExceptionBuilder;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.wsdl.v1.FileTransferService;
import com.lightcomp.ft.xsd.v1.FileTransferState;
import com.lightcomp.ft.xsd.v1.FileTransferStatus;
import com.lightcomp.ft.xsd.v1.Frame;

public class RecvOperation extends RecoverableOperation {

    private final int seqNum;

    private Frame response;

    public RecvOperation(String transferId, OperationHandler handler, int seqNum) {
        super(transferId, handler);
        this.seqNum = seqNum;
    }

    public Frame getResponse() {
        return response;
    }

    @Override
    public TransferExceptionBuilder prepareException(Throwable t) {
        return TransferExceptionBuilder.from("Failed to receive frame").addParam("seqNum", seqNum).setCause(t);
    }

    @Override
    protected boolean isFinished(FileTransferStatus status) {
        // check transfer state
        FileTransferState fts = status.getState();
        if (fts != FileTransferState.ACTIVE) {
            throw TransferExceptionBuilder.from("Invalid transfer state").addParam("name", fts).build();
        }
        // check frame seq number
        int lastSeqNum = status.getLastFrameSeqNum();
        // test if succeeded
        if (seqNum == lastSeqNum) {
            return true;
        }
        // test if match with previous frame
        if (seqNum == lastSeqNum + 1) {
            return false;
        }
        throw TransferExceptionBuilder.from("Cannot recover last received frame").addParam("seqNum", seqNum)
                .addParam("serverSeqNum", lastSeqNum).build();
    }

    @Override
    protected void send(FileTransferService service) throws FileTransferException {
        response = service.receive(BigInteger.valueOf(seqNum), transferId);
    }
}