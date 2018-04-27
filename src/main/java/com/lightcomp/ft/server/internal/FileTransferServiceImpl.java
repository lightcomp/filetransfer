package com.lightcomp.ft.server.internal;

import java.math.BigInteger;

import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.wsdl.v1.FileTransferService;
import com.lightcomp.ft.xsd.v1.FileTransferStatus;
import com.lightcomp.ft.xsd.v1.Frame;

public class FileTransferServiceImpl implements FileTransferService {

    private final TransferProvider transferProvider;

    public FileTransferServiceImpl(TransferProvider transferProvider) {
        this.transferProvider = transferProvider;
    }

    @Override
    public FileTransferStatus status(String transferId) throws FileTransferException {
        Transfer transfer = transferProvider.getTransfer(transferId);
        return transfer.getStatus();
    }

    @Override
    public void abort(String transferId) throws FileTransferException {
        Transfer transfer = transferProvider.getTransfer(transferId);
        transfer.abort();
    }
 
    @Override
    public Frame receive(BigInteger frameSeqNum, String transferId) throws FileTransferException {
        Transfer transfer = transferProvider.getTransfer(transferId);
        return transfer.sendFrame(frameSeqNum.longValue());
    }

    @Override
    public String begin(String requestId) throws FileTransferException {
        Transfer transfer = transferProvider.createTransfer(requestId);
        transfer.begin();
        return transfer.getTransferId();
    }

    @Override
    public void finish(String transferId) throws FileTransferException {
        Transfer transfer = transferProvider.getTransfer(transferId);
        transfer.finish();
    }

    @Override
    public void send(Frame frame, String transferId) throws FileTransferException {
        Transfer transfer = transferProvider.getTransfer(transferId);
        transfer.receiveFrame(frame);
    }
}
