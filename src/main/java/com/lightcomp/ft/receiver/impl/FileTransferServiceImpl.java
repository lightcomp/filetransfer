package com.lightcomp.ft.receiver.impl;

import com.lightcomp.ft.common.ChecksumType;
import com.lightcomp.ft.xsd.v1.FileChecksums;
import com.lightcomp.ft.xsd.v1.FileTransfer;
import com.lightcomp.ft.xsd.v1.FileTransferStatus;
import com.lightcomp.ft.xsd.v1.Frame;

import cxf.FileTransferException;
import cxf.FileTransferService;

public class FileTransferServiceImpl implements FileTransferService {

    private final TransferProvider transferProvider;

    public FileTransferServiceImpl(TransferProvider transferProvider) {
        this.transferProvider = transferProvider;
    }

    @Override
    public void abort(String transferId) throws FileTransferException {
        Transfer tc = transferProvider.getTransfer(transferId);
        tc.abort();
    }

    @Override
    public void commit(String transferId) throws FileTransferException {
        Transfer tc = transferProvider.getTransfer(transferId);
        tc.commit();
    }

    @Override
    public String begin(String requestId, FileTransfer fileTransfer) throws FileTransferException {
        ChecksumType cht = ChecksumType.fromValue(fileTransfer.getChecksumType());
        Transfer tc = transferProvider.createTransfer(requestId, cht);
        tc.begin(fileTransfer.getItems());
        return tc.getTransferId();
    }

    @Override
    public void prepare(String transferId, FileChecksums fileChecksums) throws FileTransferException {
        Transfer tc = transferProvider.getTransfer(transferId);
        tc.prepare(fileChecksums.getList());
    }

    @Override
    public void send(String transferId, Frame frame) throws FileTransferException {
        Transfer tc = transferProvider.getTransfer(transferId);
        tc.process(frame);
    }

    @Override
    public FileTransferStatus getStatus(String transferId) throws FileTransferException {
        Transfer tc = transferProvider.getTransfer(transferId);
        return tc.createFileTransferStatus();
    }
}
