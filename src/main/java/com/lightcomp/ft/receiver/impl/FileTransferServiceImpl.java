package com.lightcomp.ft.receiver.impl;

import com.lightcomp.ft.receiver.TransferState;
import com.lightcomp.ft.xsd.v1.FileChecksums;
import com.lightcomp.ft.xsd.v1.FileTransfer;
import com.lightcomp.ft.xsd.v1.FileTransferState;
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
        TransferImpl tc = transferProvider.getTransfer(transferId);
        tc.abort();
    }

    @Override
    public void commit(String transferId) throws FileTransferException {
        TransferImpl tc = transferProvider.getTransfer(transferId);
        tc.commit();
    }

    @Override
    public String begin(FileTransfer fileTransfer, String requestId) throws FileTransferException {
        TransferImpl tc = transferProvider.createTransfer(requestId);
        tc.begin(fileTransfer);
        return tc.getTransferId();
    }

    @Override
    public void prepare(FileChecksums fileChecksums, String transferId) throws FileTransferException {
        TransferImpl tc = transferProvider.getTransfer(transferId);
        tc.prepare(fileChecksums.getList());
    }

    @Override
    public void send(Frame frame, String transferId) throws FileTransferException {
        TransferImpl tc = transferProvider.getTransfer(transferId);
        tc.process(frame);
    }

    @Override
    public FileTransferStatus getStatus(String transferId) throws FileTransferException {
        TransferImpl tc = transferProvider.getTransfer(transferId);
        TransferStatusImpl ts = tc.getStatus();

        FileTransferStatus fts = new FileTransferStatus();
        fts.setLastReceivedFrameId(ts.getLastReceivedFrameId());
        fts.setState(convertState(ts.getState()));

        return fts;
    }

    private static FileTransferState convertState(TransferState state) {
        switch (state) {
            case COMMITTED:
                return FileTransferState.COMMITTED;
            case FAILED:
            case CANCELED:
                return FileTransferState.FAILED;
            case PREPARED:
                return FileTransferState.PREPARED;
            default:
                return FileTransferState.ACTIVE;
        }
    }
}
