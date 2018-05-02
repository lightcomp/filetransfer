package com.lightcomp.ft.server.internal;

import com.lightcomp.ft.wsdl.v1.FileTransferException;
import com.lightcomp.ft.xsd.v1.Frame;

public interface Transfer {

    String getTransferId();

    void recvFrame(Frame frame) throws FileTransferException;

    Frame sendFrame(long seqNum) throws FileTransferException;

    void finish() throws FileTransferException;

    void abort() throws FileTransferException;
}
