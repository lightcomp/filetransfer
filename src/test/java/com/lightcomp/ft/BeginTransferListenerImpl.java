package com.lightcomp.ft;

import java.nio.file.Path;

import com.lightcomp.ft.receiver.BeginTransferListener;
import com.lightcomp.ft.receiver.TransferAcceptor;

public class BeginTransferListenerImpl implements BeginTransferListener {

    @Override
    public TransferAcceptor onTransferBegin(String requestId) {
        return new TransferAcceptor() {

            @Override
            public void onTransferSuccess() {
                // TODO Auto-generated method stub

            }

            @Override
            public void onTransferFailed(Throwable cause) {
                // TODO Auto-generated method stub

            }

            @Override
            public void onTransferAbort() {
                // TODO Auto-generated method stub

            }

            @Override
            public String getTransferId() {
                // TODO Auto-generated method stub
                return "X";
            }

            @Override
            public Path getTransferDir() {
                // TODO Auto-generated method stub
                return null;
            }
        };
    }

}
