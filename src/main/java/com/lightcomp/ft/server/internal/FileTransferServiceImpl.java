package com.lightcomp.ft.server.internal;

import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.wsdl.v1.FileTransferService;
import com.lightcomp.ft.xsd.v1.AbortRequest;
import com.lightcomp.ft.xsd.v1.BeginResponse;
import com.lightcomp.ft.xsd.v1.FileTransferState;
import com.lightcomp.ft.xsd.v1.FinishRequest;
import com.lightcomp.ft.xsd.v1.Frame;
import com.lightcomp.ft.xsd.v1.GenericDataType;
import com.lightcomp.ft.xsd.v1.ReceiveRequest;
import com.lightcomp.ft.xsd.v1.SendRequest;
import com.lightcomp.ft.xsd.v1.TransferStatus;
import com.lightcomp.ft.xsd.v1.TransferStatusRequest;

public class FileTransferServiceImpl implements FileTransferService {

    private final TransferManager manager;

    public FileTransferServiceImpl(TransferManager transferProvider) {
        this.manager = transferProvider;
    }

    @Override
    public BeginResponse begin(GenericDataType beginRequest) throws FileTransferException {
        String transferId = manager.createTransferAsync(beginRequest);
        BeginResponse br = new BeginResponse();
        br.setTransferId(transferId);
        return br;
    }

    @Override
    public Frame receive(ReceiveRequest receiveRequest) throws FileTransferException {
        Transfer transfer = manager.getTransfer(receiveRequest.getTransferId());
        return transfer.sendFrame(receiveRequest.getFrameSeqNum());
    }

    @Override
    public void send(SendRequest sendRequest) throws FileTransferException {
        Transfer transfer = manager.getTransfer(sendRequest.getTransferId());
        transfer.recvFrame(sendRequest.getFrame());
    }

    @Override
    public GenericDataType finish(FinishRequest finishRequest) throws FileTransferException {
        Transfer transfer = manager.getTransfer(finishRequest.getTransferId());
        return transfer.finish();
    }

    @Override
    public TransferStatus status(TransferStatusRequest statusRequest) throws FileTransferException {
        com.lightcomp.ft.server.TransferStatus ts = manager.getConfirmedStatus(statusRequest.getTransferId());
        return convertServerStatus(ts);
    }

    @Override
    public void abort(AbortRequest abortRequest) throws FileTransferException {
        Transfer transfer = manager.getTransfer(abortRequest.getTransferId());
        transfer.abort();
    }

    public static TransferStatus convertServerStatus(com.lightcomp.ft.server.TransferStatus serverStatus) {
        TransferStatus ts = new TransferStatus();
        ts.setLastFrameSeqNum(serverStatus.getLastFrameSeqNum());
        // convert state
        switch (serverStatus.getState()) {
            case CREATED:
            case STARTED:
            case TRANSFERED:
            case FINISHING:
                ts.setState(FileTransferState.ACTIVE);
                break;
            case FINISHED:
                ts.setState(FileTransferState.FINISHED);
                break;
            case CANCELED:
            case FAILED:
                ts.setState(FileTransferState.FAILED);
                break;
        }
        return ts;
    }
}
