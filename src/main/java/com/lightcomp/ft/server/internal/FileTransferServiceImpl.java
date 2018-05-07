package com.lightcomp.ft.server.internal;

import java.math.BigInteger;

import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.wsdl.v1.FileTransferService;
import com.lightcomp.ft.xsd.v1.FileTransferStatus;
import com.lightcomp.ft.xsd.v1.Frame;
import com.lightcomp.ft.xsd.v1.GenericData;

public class FileTransferServiceImpl implements FileTransferService {

    private final TransferManager manager;

    public FileTransferServiceImpl(TransferManager transferProvider) {
        this.manager = transferProvider;
    }

    @Override
    public FileTransferStatus status(String transferId) throws FileTransferException {
        return manager.getFileTransferStatus(transferId);
    }

    @Override
    public void abort(String transferId) throws FileTransferException {
        Transfer transfer = manager.getTransfer(transferId);
        transfer.abort();
    }

    @Override
    public Frame receive(BigInteger frameSeqNum, String transferId) throws FileTransferException {
        Transfer transfer = manager.getTransfer(transferId);
        return transfer.sendFrame(frameSeqNum.longValue());
    }

    @Override
    public String begin(GenericData request) throws FileTransferException {
        Transfer transfer = manager.createTransfer(request);
        return transfer.getTransferId();
    }

    @Override
    public GenericData finish(String transferId) throws FileTransferException {
        Transfer transfer = manager.getTransfer(transferId);
        return transfer.finish();
    }

    @Override
    public void send(Frame frame, String transferId) throws FileTransferException {
        Transfer transfer = manager.getTransfer(transferId);
        transfer.recvFrame(frame);
    }
}
