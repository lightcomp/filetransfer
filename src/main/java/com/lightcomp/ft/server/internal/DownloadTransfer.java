package com.lightcomp.ft.server.internal;

import com.lightcomp.ft.common.TaskExecutor;
import com.lightcomp.ft.server.DownloadHandler;
import com.lightcomp.ft.server.ServerConfig;
import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.xsd.v1.Frame;

public class DownloadTransfer extends AbstractTransfer {

    protected DownloadTransfer(String transferId, DownloadHandler handler, ServerConfig config, TaskExecutor executor) {
        super(transferId, handler, config, executor);
    }

    @Override
    public synchronized boolean isBusy() {
        // TODO Auto-generated method stub
        return super.isBusy();
    }

    @Override
    public void recvFrame(Frame frame) throws FileTransferException {
        // TODO Auto-generated method stub
    }

    @Override
    public Frame sendFrame(long seqNum) throws FileTransferException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected void checkPreparedFinish() throws FileTransferException {
        // TODO Auto-generated method stub
    }

    @Override
    protected void clearResources() {
        // TODO Auto-generated method stub
    }
}
