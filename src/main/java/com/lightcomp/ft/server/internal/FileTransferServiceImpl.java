package com.lightcomp.ft.server.internal;

import com.lightcomp.ft.server.TransferState;
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

import jakarta.xml.ws.WebServiceProvider;

@WebServiceProvider(targetNamespace = "http://www.lightcomp.com/ft/wsdl/v1", serviceName = "FileTransferService")
public class FileTransferServiceImpl implements FileTransferService {

    private final TransferManager manager;

    public FileTransferServiceImpl(TransferManager manager) {
        this.manager = manager;
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
        Transfer transfer = manager.getTransfer(statusRequest.getTransferId());

        com.lightcomp.ft.server.TransferStatus cs = transfer.getConfirmedStatus();
        TransferStatus ts = new TransferStatus();
        ts.setLastFrameSeqNum(cs.getTransferedSeqNum());
        ts.setResp(cs.getResponse());
        ts.setState(convertState(cs.getState()));
        return ts;
    }

    @Override
    public void abort(AbortRequest abortRequest) throws FileTransferException {
        Transfer transfer = manager.getTransfer(abortRequest.getTransferId());
        transfer.abort();
    }

    private static FileTransferState convertState(TransferState state) {
        switch (state) {
        case STARTED:
        case TRANSFERED:
            return FileTransferState.ACTIVE;
        case FINISHED:
            return FileTransferState.FINISHED;
        case FAILED:
        case CANCELED:
        case ABORTED:
            return FileTransferState.FAILED;
        default:
            throw new IllegalStateException();
        }
    }
}
